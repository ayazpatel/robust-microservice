package app.service.store;

import app.service.model.ContactMin;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
public class RedisContactsStore implements ContactsStore {
    private static final Logger log = LoggerFactory.getLogger(RedisContactsStore.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public RedisContactsStore(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis; this.om = om;
    }

    // Unified key layout per device (single JSON array for contacts)
    private String prefixKey(String appId, String adminId, String deviceId) {
    return "app:" + nz(appId) + ":" + nz(adminId) + ":" + nz(deviceId);
    }

    @Override
    public void upsertSnapshot(String adminId, String deviceId, Instant collectedAt, List<ContactMin> contacts) {
        if (contacts == null || contacts.isEmpty()) return;
        // Build unified minimal array and replace existing value
        String appId = (contacts.get(0).getAppId() != null && !contacts.get(0).getAppId().isBlank()) ? contacts.get(0).getAppId() : "app";
        Instant ts = (collectedAt != null ? collectedAt : Instant.now());
        try {
            var arr = om.createArrayNode();
            for (ContactMin c : contacts) {
                if (c == null) continue;
                if (c.getLookupKey() == null || c.getLookupKey().isBlank()) continue;
                var node = om.createObjectNode();
                node.put("lookupKey", c.getLookupKey());
                if (c.getDisplayName() != null) node.put("displayName", c.getDisplayName());
                var nums = om.createArrayNode();
                if (c.getNumbers() != null) {
                    for (String n : c.getNumbers()) if (n != null) nums.add(n);
                }
                node.set("numbers", nums);
                arr.add(node);
            }
            var payload = om.createObjectNode();
            payload.set("contacts", arr);
            payload.put("collectedAt", ts.toString());
            String json = om.writeValueAsString(payload.get("contacts")); // store only the contacts array per requirement
            String key = prefixKey(appId, adminId, deviceId);
            redis.opsForHash().put(Objects.requireNonNull(key), "contacts", Objects.requireNonNull(json));
            log.info("contacts upsertSnapshot unified admin={} device={} size={}", adminId, deviceId, contacts.size());
        } catch (Exception e) {
            log.error("contacts unified upsert failed", e);
        }
    }

    @Override
    public List<ContactMin> list(ContactsQuery q) {
    String adminId = Objects.toString(q.getAdminId(), "");
    String appId = Objects.toString(q.getAppId(), "");
        String deviceId = q.getDeviceId();
        int page = Math.max(0, q.getPage());
        int size = Math.max(1, Math.min(200, q.getSize()));
        List<ContactMin> out = new ArrayList<>();
        if (deviceId != null && !deviceId.isBlank()) {
            listFromDevice(out, appId, adminId, deviceId, page, size, q.getQ());
        } else {
            listFromAdmin(out, appId, adminId, page, size, q.getQ());
        }
        return out;
    }

    private void listFromDevice(List<ContactMin> out, String appId, String adminId, String deviceId, int page, int size, String q) {
        // Prefer unified aggregator snapshot at /app/{appId}/{adminId}/{deviceId}/contacts
    String unifiedKey = "app:" + nz(appId) + ":" + nz(adminId) + ":" + nz(deviceId);
        try {
            String unified = (String) redis.opsForHash().get(unifiedKey, "contacts");
            if (unified != null && !unified.isBlank()) {
                // unified is a JSON array of minimal contacts {lookupKey, displayName, numbers[]}
                com.fasterxml.jackson.databind.JsonNode arr = om.readTree(unified);
                if (arr.isArray()) {
                    int start = page * size;
                    int end = start + size; // exclusive
                    int i = 0;
                    for (var it : arr) {
                        if (i >= end) break;
                        if (i++ < start) continue;
                        try {
                            String lookup = optText(it, "lookupKey");
                            String name = optText(it, "displayName");
                            List<String> numbers = new ArrayList<>();
                            var nums = it.path("numbers");
                            if (nums.isArray()) nums.forEach(n -> { if (!n.isNull()) numbers.add(n.asText()); });
                            ContactMin c = new ContactMin();
                            c.setAdminId(adminId);
                            c.setAppId(appId);
                            c.setDeviceId(deviceId);
                            c.setLookupKey(lookup);
                            c.setDisplayName(name);
                            c.setNumbers(numbers);
                            c.setCollectedAt(Instant.now());
                            c.setCreatedAt(c.getCollectedAt());
                            c.setUpdatedAt(c.getCollectedAt());
                            if (lookup != null && !lookup.isBlank()) c.setId(computeId(adminId, deviceId, lookup));
                            if (matches(c, q)) out.add(c);
                        } catch (Exception mapEx) {
                            log.warn("contacts unified map error: {}", mapEx.toString());
                        }
                    }
                    return; // served from unified snapshot
                }
            }
        } catch (Exception e) {
            log.warn("contacts unified read error: {}", e.toString());
        }

        // No unified data present; nothing to return under new model
    }

    private void listFromAdmin(List<ContactMin> out, String appId, String adminId, int page, int size, String q) { /* not supported in unified model without deviceId */ }

    private boolean matches(ContactMin c, String q) {
        if (c == null) return false;
        if (q == null || q.isBlank()) return true;
        String dn = c.getDisplayName();
        return dn != null && dn.toLowerCase().contains(q.toLowerCase());
    }

    private long computeId(String adminId, String deviceId, String lookupKey) {
        long h = 1125899906842597L;
        String s = adminId + '|' + deviceId + '|' + lookupKey;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return h < 0 ? -h : h;
    }

    private static String nz(String s){ return s==null? "" : s; }

    private static String optText(com.fasterxml.jackson.databind.JsonNode n, String field) {
        if (n == null) return null;
        var v = n.path(field);
        return (v != null && !v.isMissingNode() && !v.isNull()) ? v.asText() : null;
    }
}
