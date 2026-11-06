package app.service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class AdminNotifier {
    private static final Logger log = LoggerFactory.getLogger(AdminNotifier.class);

    @Value("${admin.refresh-url:http://localhost:8090/api/push}")
    private String refreshUrl;

    private final RestTemplate http = new RestTemplate();

    public void notifyRefresh(String adminId, String kind) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("adminId", adminId);
            body.put("kind", kind);
            http.postForEntity(refreshUrl, new HttpEntity<>(body, h), String.class);
        } catch (Exception e) {
            log.warn("Admin refresh notify failed: {}", e.getMessage());
        }
    }
}
