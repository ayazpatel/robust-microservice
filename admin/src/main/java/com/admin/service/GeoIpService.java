package com.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GeoIpService {
    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${geo.lookup.enabled:false}")
    private boolean enabled;

    // Default to ip-api.com simple JSON
    @Value("${geo.lookup.url:https://ip-api.com/json/{ip}?fields=status,country,regionName,city}")
    private String lookupUrl;

    public String lookupCityCountry(String ip) {
        if (!enabled || ip == null || ip.isBlank()) return null;
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(lookupUrl, Map.class, ip);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) return null;
            Object status = resp.getBody().get("status");
            if (status == null || !"success".equalsIgnoreCase(status.toString())) return null;
            String city = asString(resp.getBody().get("city"));
            String region = asString(resp.getBody().get("regionName"));
            String country = asString(resp.getBody().get("country"));
            StringBuilder sb = new StringBuilder();
            if (city != null && !city.isBlank()) sb.append(city);
            if (region != null && !region.isBlank()) sb.append(sb.length()>0?", ":"").append(region);
            if (country != null && !country.isBlank()) sb.append(sb.length()>0?", ":"").append(country);
            return sb.length() == 0 ? null : sb.toString();
        } catch (RestClientException ex) {
            log.debug("Geo lookup failed for {}", ip);
            return null;
        }
    }

    private static String asString(Object o) { return o == null ? null : o.toString(); }
}
