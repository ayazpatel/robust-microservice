package app.service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class AdminNotifier {

    private static final Logger log = LoggerFactory.getLogger(AdminNotifier.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();

    @Value("${admin.refresh-url:}")
    private String refreshUrl;

    public void notifyRefresh(String adminId, String type) {
        if (refreshUrl == null || refreshUrl.isBlank()) return; // disabled

        try {
            Map<String,Object> body = Map.of(
                    "adminId", adminId,
                    "type", type,
                    "payload", Map.of("status","new db entry")
            );
            String json = om.writeValueAsString(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(refreshUrl, new HttpEntity<>(json, headers), Void.class);
            log.info("Notified admin {} with {}", adminId, type);
        } catch (Exception e) {
            log.info("Admin {} likely offline; skipping. {}", adminId, e.getMessage());
        }
    }
}
