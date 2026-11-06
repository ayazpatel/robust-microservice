package com.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class AppsClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${apps.api.base-url:http://localhost:8081}")
    private String baseUrl;

    public List<AppInfo> getApps() {
        try {
            ResponseEntity<AppInfo[]> resp = restTemplate.getForEntity(baseUrl + "/api/apps", AppInfo[].class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return Arrays.asList(resp.getBody());
            }
        } catch (RestClientException ignored) {}
        return Collections.emptyList();
    }

    public static class AppInfo {
        public Long id;
        public String appId;
        public String apiKey;
        public String packageName;
        public String signatureSha;
        public String appName;
    }
}

