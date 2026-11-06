package com.admingateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

@Component
public class SessionValidator {
    private final RestTemplate rest = new RestTemplate();

    @Value("${gateway.admin-base-url:http://localhost:8082}")
    private String adminBaseUrl;
    @Value("${gateway.validate-path:/auth/session/validate}")
    private String validatePath;

    public Result validate(String sessionId) {
        try {
            if (sessionId == null || sessionId.isBlank()) return Result.inactive();
            HttpHeaders h = new HttpHeaders();
            h.add("X-Session-Id", sessionId);
            ResponseEntity<Map> resp = rest.exchange(URI.create(adminBaseUrl + validatePath),
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<Void>(h), Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) return Result.inactive();
            Object active = resp.getBody().get("active");
            if (!(active instanceof Boolean b) || !b) return Result.inactive();
            String adminId = String.valueOf(resp.getBody().get("adminId"));
            Object appIdObj = resp.getBody().get("appId");
            String appId = appIdObj == null ? null : String.valueOf(appIdObj);
            return Result.active(adminId, appId);
        } catch (Exception e) { return Result.inactive(); }
    }

    public record Result(boolean active, String adminId, String appId) {
        public static Result inactive(){return new Result(false, null, null);} 
        public static Result active(String adminId, String appId){return new Result(true, adminId, appId);} }
}

