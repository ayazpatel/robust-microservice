package com.admin.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {
    private static class Counter {
        int count;
        Instant windowStart;
    }
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, int limit, int windowSeconds) {
        Instant now = Instant.now();
        Counter c = counters.computeIfAbsent(key, k -> {
            Counter x = new Counter();
            x.count = 0;
            x.windowStart = now;
            return x;
        });
        synchronized (c) {
            if (now.isAfter(c.windowStart.plusSeconds(windowSeconds))) {
                c.windowStart = now;
                c.count = 0;
            }
            if (c.count >= limit) return false;
            c.count++;
            return true;
        }
    }
}

