package app.socket.auth.web;

import app.socket.auth.model.AppRegistration;
import app.socket.auth.repo.AppRegistrationRepository;
import app.socket.auth.service.AppService;
import app.socket.auth.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AppService appService;
    private final AppRegistrationRepository repo;
    private final JwtService jwtService;

    public AuthController(AppService appService, AppRegistrationRepository repo, JwtService jwtService) {
        this.appService = appService;
        this.repo = repo;
        this.jwtService = jwtService;
    }

    // 1) Verify app auth and mint JWT for websocket
    @PostMapping("/auth/token")
    public ResponseEntity<?> token(@RequestBody Map<String, String> body, @RequestHeader(value = "X-Forwarded-For", required = false) String xff) {
        String appId = body.getOrDefault("appId", "");
        String apiKey = body.getOrDefault("apiKey", "");
        String packageName = body.getOrDefault("packageName", "");
        String signatureSha = body.getOrDefault("signatureSha", "");
        String deviceId = body.getOrDefault("deviceId", "");
        String ip = body.getOrDefault("ip", null);
        long ttl = Long.parseLong(body.getOrDefault("ttlSeconds", "86400"));

        AppRegistration ar = repo.findByAppId(appId).orElse(null);
        if (ar == null) return ResponseEntity.status(401).body(Map.of("error", "invalid_app"));
        if (!ar.getApiKey().equals(apiKey)) return ResponseEntity.status(401).body(Map.of("error", "invalid_key"));
        if (!ar.getPackageName().equals(packageName)) return ResponseEntity.status(401).body(Map.of("error", "invalid_package"));
        if (!ar.getSignatureSha().equals(signatureSha)) return ResponseEntity.status(401).body(Map.of("error", "invalid_signature"));
        if (deviceId == null || deviceId.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "missing_deviceId"));

        String ipFinal = (ip != null && !ip.isBlank()) ? ip : (xff != null ? xff.split(",")[0].trim() : "");
        String jwt = jwtService.issue(appId, deviceId, ipFinal, ttl);
        return ResponseEntity.ok(Map.of("token", jwt));
    }

    // 2) Register app
    @PostMapping("/apps")
    public AppRegistration register(@RequestBody Map<String, String> body) {
        String packageName = body.get("packageName");
        String signatureSha = body.get("signatureSha");
        String appName = body.getOrDefault("appName", null);
        return appService.create(packageName, signatureSha, appName);
    }

    // 3) List apps
    @GetMapping("/apps")
    public List<AppRegistration> list() {
        return appService.list();
    }

    // 4) Reset app api key
    @PostMapping("/apps/{appId}/reset-key")
    public AppRegistration resetKey(@PathVariable String appId) {
        return appService.resetApiKey(appId);
    }

    // 5) Update app (no change to appId)
    @PutMapping("/apps/{appId}")
    public AppRegistration update(@PathVariable String appId, @RequestBody Map<String, String> body) {
        String packageName = body.get("packageName");
        String signatureSha = body.get("signatureSha");
        String appName = body.getOrDefault("appName", null);
        return appService.update(appId, packageName, signatureSha, appName);
    }

    // 6) Delete app
    @DeleteMapping("/apps/{appId}")
    public ResponseEntity<?> delete(@PathVariable String appId) {
        appService.delete(appId);
        return ResponseEntity.noContent().build();
    }
}
