package app.service.store;

import app.service.model.MessageForward;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class MessageForwardStore {
    // Snapshot-only: no external dedupe keys

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public MessageForwardStore(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis; this.om = om;
    }

    private static String prefixKey(String appId, String adminId, String deviceId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":" + nz(deviceId);
    }

    public void saveAll(Collection<MessageForward> items) {
        if (items == null || items.isEmpty()) return;
        Map<String, List<MessageForward>> byKey = new LinkedHashMap<>();
        for (MessageForward m : items) {
            String key = prefixKey(nz(m.getAppId()), nz(m.getAdminId()), nz(m.getDeviceId()));
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        for (Map.Entry<String,List<MessageForward>> e : byKey.entrySet()) {
            String key = e.getKey();
            List<Map<String,Object>> out = new ArrayList<>(e.getValue().size());
            for (MessageForward m : e.getValue()) out.add(toMap(m));
            try {
                redis.opsForHash().put(key, "messageforwarding", om.writeValueAsString(out));
            } catch (Exception ignore) {
                // store replace failure ignored: upstream notifier will retry on next payload
            }
        }
    }

    @SuppressWarnings("unused")
    public boolean exists(String adminId, String deviceId, String recipient, String forwardTo, Instant ts) {
        // In snapshot mode we always replace; no existence check
        return false;
    }

    private static Map<String,Object> toMap(MessageForward m){
        Map<String,Object> d = new LinkedHashMap<>();
        d.put("appId", m.getAppId());
        d.put("adminId", m.getAdminId());
        d.put("deviceId", m.getDeviceId());
        d.put("recipient", m.getRecipient());
        d.put("forwardTo", m.getForwardTo());
        d.put("messageContent", m.getMessageContent());
        d.put("status", m.getStatus());
        d.put("eventTimestamp", m.getEventTimestamp()!=null? m.getEventTimestamp().toString() : Instant.now().toString());
        return d;
    }
    // no parse/token helpers needed in snapshot mode
    private static String nz(String s){ return s==null? "" : s; }
    // no hashing required; snapshot fully replaces
}
