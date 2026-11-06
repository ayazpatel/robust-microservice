package app.service.web;

import app.service.model.TelemetryRecord;
import app.service.store.TelemetryQuery;
import app.service.store.TelemetryStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryApiController {
    private final TelemetryStore store;
    private final SessionValidator sessionValidator;

    public TelemetryApiController(TelemetryStore store, SessionValidator sessionValidator) {
        this.store = store; this.sessionValidator = sessionValidator;
    }

    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> list(@CookieValue(value = "asid", required = false) String asid,
                                  @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
                                  @RequestParam(value = "deviceId", required = false) String deviceId,
                                  @RequestParam(value = "from", required = false) String from,
                                  @RequestParam(value = "to", required = false) String to,
                                  @RequestParam(value = "page", defaultValue = "0") int page,
                                  @RequestParam(value = "size", defaultValue = "50") int size) {
        String sid = sidHeader != null && !sidHeader.isBlank() ? sidHeader : asid;
        var v = sessionValidator.validate(sid);
        if (!v.active()) return ResponseEntity.status(401).build();

    TelemetryQuery q = new TelemetryQuery();
    q.setAdminId(v.adminId());
    q.setAppId(v.appId());
        q.setDeviceId(deviceId);
    q.setFrom(parseInstant(from));
    q.setTo(parseInstant(to));
        q.setPage(Math.max(0, page));
        q.setSize(Math.max(1, Math.min(200, size)));

        List<TelemetryRecord> items = store.list(q);
        Map<String, Object> out = new HashMap<>();
        out.put("items", items.stream().map(TelemetryApiController::toDto).toList());
        out.put("page", q.getPage());
        out.put("size", q.getSize());
        out.put("count", items.size());
        return ResponseEntity.ok(out);
    }

    private static Map<String,Object> toDto(TelemetryRecord r) {
        Map<String,Object> m = new HashMap<>();
        m.put("id", r.getDeviceId());
        m.put("deviceId", r.getDeviceId());
        m.put("eventTs", r.getEventTs());
        m.put("androidVersion", r.getAndroidVersion());
        m.put("modelName", r.getModelName());
        m.put("batteryLevel", r.getBatteryLevel());
        m.put("cpuArch", r.getCpuArch());
        m.put("sdkVersion", r.getSdkVersion());
        m.put("isRoot", r.getIsRoot());
        m.put("isEmulator", r.getIsEmulator());
        m.put("isSdCard", r.getIsSdCard());
        m.put("storage", r.getStorage());
        m.put("serviceProvider", r.getServiceProvider());
        m.put("networkType", r.getNetworkType());
        return m;
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
    try { return Instant.parse(s.trim()); } catch (Exception ignored) { /* try next format */ }
        // epoch millis
    try { return Instant.ofEpochMilli(Long.parseLong(s.trim())); } catch (Exception ignored) { /* try seconds */ }
        // epoch seconds
    try { return Instant.ofEpochSecond(Long.parseLong(s.trim())); } catch (Exception ignored) { /* give up */ }
        return null;
    }
}
