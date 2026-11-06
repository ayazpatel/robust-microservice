package com.admin.service;

import com.admin.model.Admin;
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

@Service
public class TelegramBotService {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${telegram.bot.token}")
    private String botToken;

    public boolean sendLoginOtp(Admin admin, String code) {
        String chatId = admin.getTelegramUserId();
        if (chatId == null || chatId.isBlank()) {
            log.warn("Admin {} has no Telegram user id configured", admin.getUsername());
            return false;
        }
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        String text = "OTP: " + code + " (login)";
        String body = "{\"chat_id\":\"" + chatId + "\",\"text\":\"" + text + "\"}";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            if (!ok) log.warn("OTP send returned non-2xx for {}: {}", admin.getUsername(), resp.getStatusCode());
            return ok;
        } catch (RestClientException ex) {
            log.error("Failed to send OTP to Telegram chat for {}", admin.getUsername(), ex);
            return false;
        }
    }

    public void sendNewIpAlert(Admin admin, String ip, String userAgent) {
        String chatId = admin.getTelegramUserId();
        if (chatId == null || chatId.isBlank()) return;
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        String safeUa = userAgent == null ? "" : userAgent.replace("\"", "'");
        String text = "New login: IP " + (ip == null ? "unknown" : ip) + (safeUa.isEmpty() ? "" : " | UA: " + safeUa);
        String body = "{\"chat_id\":\"" + chatId + "\",\"text\":\"" + text + "\"}";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, String.class);
        } catch (RestClientException ex) {
            log.warn("Failed to send new IP alert for {}", admin.getUsername(), ex);
        }
    }

    public void sendSecurityNotice(Admin admin, String message) {
        String chatId = admin.getTelegramUserId();
        if (chatId == null || chatId.isBlank()) return;
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        String body = "{\"chat_id\":\"" + chatId + "\",\"text\":\"" + message.replace("\"","'") + "\"}";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, String.class);
        } catch (RestClientException ex) {
            log.warn("Failed to send security notice for {}", admin.getUsername(), ex);
        }
    }
}

