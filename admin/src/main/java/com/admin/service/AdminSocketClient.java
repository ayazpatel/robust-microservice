package com.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AdminSocketClient {
    private static final Logger log = LoggerFactory.getLogger(AdminSocketClient.class);
    private final RestTemplate rest = new RestTemplate();

    @Value("${admin.socket.base-url:http://localhost:8090}")
    private String baseUrl;

    public void kick(String adminId) {
        try {
            String url = baseUrl + "/api/kick-admin";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> req = new HttpEntity<>(Map.of("adminId", adminId), headers);
            ResponseEntity<String> resp = rest.postForEntity(url, req, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("kick-admin returned {}", resp.getStatusCode());
            }
        } catch (RestClientException e) {
            log.debug("kick-admin failed: {}", e.getMessage());
        }
    }
}

