package app.service.web;

import app.service.config.AdminProps;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


@Component
public class SessionValidator {
    private final RestTemplate rest = new RestTemplate();

    private final AdminProps adminProps;

    public SessionValidator(AdminProps adminProps) { this.adminProps = adminProps; }

    public Result validate(String sessionId) {
        try {
            if (sessionId == null || sessionId.isBlank()) return Result.inactive();
            HttpHeaders h = new HttpHeaders(); h.add("X-Session-Id", sessionId);
            String base = adminProps.getBaseUrl() != null ? adminProps.getBaseUrl() : "http://localhost:8082";
            String path = adminProps.getValidatePath() != null ? adminProps.getValidatePath() : "/auth/session/validate";
        ResponseEntity<java.util.Map<String,Object>> resp = rest.exchange(
            java.net.URI.create(base + path),
            org.springframework.http.HttpMethod.GET,
            new org.springframework.http.HttpEntity<Void>(h),
            new org.springframework.core.ParameterizedTypeReference<java.util.Map<String,Object>>(){});
            if (!resp.getStatusCode().is2xxSuccessful()) return Result.inactive();
        java.util.Map<String,Object> body = resp.getBody();
        if (body == null) return Result.inactive();
            Object active = body.get("active");
            if (!(active instanceof Boolean b) || !b.booleanValue()) return Result.inactive();
        String adminId = String.valueOf(body.get("adminId"));
        Object appIdObj = body.get("appId");
        String appId = appIdObj==null? null : String.valueOf(appIdObj);
            return Result.active(adminId, appId);
        } catch (Exception e) { return Result.inactive(); }
    }

    public record Result(boolean active, String adminId, String appId) {
        public static Result inactive(){return new Result(false, null, null);} public static Result active(String adminId, String appId){return new Result(true, adminId, appId);} }
}

