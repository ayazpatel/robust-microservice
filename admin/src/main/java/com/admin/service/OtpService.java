package com.admin.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {
    private static final SecureRandom RNG = new SecureRandom();

    private static class Entry {
        final String code;
        final Instant expiresAt;
        Entry(String code, Instant expiresAt) { this.code = code; this.expiresAt = expiresAt; }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public String generate(String key, int ttlSeconds) {
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        store.put(key, new Entry(code, Instant.now().plusSeconds(ttlSeconds)));
        return code;
    }

    public boolean verify(String key, String code) {
        Entry e = store.get(key);
        if (e == null) return false;
        if (Instant.now().isAfter(e.expiresAt)) {
            store.remove(key);
            return false;
        }
        boolean ok = Objects.equals(e.code, code);
        if (ok) store.remove(key);
        return ok;
    }
}

