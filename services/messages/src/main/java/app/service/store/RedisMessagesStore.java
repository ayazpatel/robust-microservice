package app.service.store;

import app.service.common.MessageStatus;
import app.service.common.MessageType;
import app.service.model.Messages;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
public class RedisMessagesStore implements MessagesStore {

    // Snapshot-only: no external dedupe keys

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public RedisMessagesStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.om = objectMapper;
    }

    @Override
    public boolean existsByAdminAndDeviceAndRecipientAtTs(String adminId, String deviceId, String recipient, Instant ts) {
        // In snapshot mode we always replace; no dedupe existence checks
        return false;
    }

    @Override
    public void saveAll(List<Messages> items) {
        if (items == null || items.isEmpty()) return;

    // Store messages as a Redis hash per device prefix.
    Map<String, List<Messages>> byDevice = new LinkedHashMap<>();
        for (Messages m : items) {
            String key = unifiedKey(m.getAppId(), m.getAdminId(), m.getDeviceId());
            byDevice.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }

        for (Map.Entry<String, List<Messages>> e : byDevice.entrySet()) {
            String key = e.getKey();
            List<Messages> batch = e.getValue();
            List<Map<String, Object>> current = new ArrayList<>(batch.size());
            for (Messages m : batch) current.add(toMap(m));
            try {
                String json = om.writeValueAsString(current);
                // Store under device prefix as hash field 'messages'
                String prefix = prefixKey(extractAppId(batch), extractAdminId(batch), extractDeviceId(batch));
                redis.opsForHash().put(prefix, "messages", json);
            } catch (Exception ignore) {}
        }
    }

    @Override
    public List<Messages> list(MessagesQuery q) {
        int page = Math.max(0, q.page());
        int size = Math.max(1, Math.min(q.size(), 200));
        long now = System.currentTimeMillis();
        long from = q.from() != null ? q.from().toEpochMilli() : 0L;
        long to = q.to() != null ? q.to().toEpochMilli() : now;

    String prefix = prefixKey(q.appId(), q.adminId(), q.deviceId());
    String json = (String) redis.opsForHash().get(prefix, "messages");
        if (json == null || json.isBlank()) return List.of();
        List<Messages> all = new ArrayList<>();
        try {
            java.util.List<java.util.Map<String,Object>> list = om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String,Object>>>(){});
            for (java.util.Map<String,Object> map : list) {
                Messages m = fromMap(map);
                if (m.getEventTimestamp()!=null) {
                    long ts = m.getEventTimestamp().toEpochMilli();
                    if (from > 0 && ts < from) continue;
                    if (to > 0 && ts > to) continue;
                }
                if (q.recipient() != null && !q.recipient().isBlank() && !q.recipient().equals(m.getRecipient())) continue;
                if (q.status() != null && q.status() != m.getMessageStatus()) continue;
                if (q.type() != null && q.type() != m.getMessageType()) continue;
                all.add(m);
            }
        } catch (Exception e) {
            return List.of();
        }
        // Sort desc by event time
        all.sort((a,b) -> {
            long am = a.getEventTimestamp()!=null? a.getEventTimestamp().toEpochMilli() : 0L;
            long bm = b.getEventTimestamp()!=null? b.getEventTimestamp().toEpochMilli() : 0L;
            return Long.compare(bm, am);
        });
        int start = Math.min(page * size, all.size());
        int end = Math.min(start + size, all.size());
        if (start >= end) return List.of();
        return new ArrayList<>(all.subList(start, end));
    }

    private static String unifiedKey(String appId, String adminId, String deviceId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":" + nz(deviceId) + ":messages:data";
    }

    private static String prefixKey(String appId, String adminId, String deviceId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":" + nz(deviceId);
    }
    private static Map<String,Object> toMap(Messages m) {
        Map<String,Object> d = new LinkedHashMap<>();
        d.put("appId", m.getAppId());
        d.put("adminId", m.getAdminId());
        d.put("deviceId", m.getDeviceId());
        d.put("recipient", m.getRecipient());
        d.put("messageContent", m.getMessageContent());
        d.put("subscriptionId", m.getSubscriptionId());
        d.put("simSlotIndex", m.getSimSlotIndex());
        d.put("carrierName", m.getCarrierName());
        d.put("mccMnc", m.getMccMnc());
        d.put("messageStatus", m.getMessageStatus() != null ? m.getMessageStatus().name() : null);
        d.put("messageType", m.getMessageType() != null ? m.getMessageType().name() : null);
        d.put("eventTimestamp", m.getEventTimestamp() != null ? m.getEventTimestamp().toString() : Instant.now().toString());
        return d;
    }

    private Messages fromMap(Map<?,?> d) {
        Messages m = new Messages();
    m.setAppId(asStr(d.get("appId")));
    m.setAdminId(asStr(d.get("adminId")));
        m.setDeviceId(asStr(d.get("deviceId")));
        m.setRecipient(asStr(d.get("recipient")));
        m.setMessageContent(asStr(d.get("messageContent")));
        m.setSubscriptionId(asInt(d.get("subscriptionId")));
        m.setSimSlotIndex(asInt(d.get("simSlotIndex")));
        m.setCarrierName(asStr(d.get("carrierName")));
        m.setMccMnc(asStr(d.get("mccMnc")));
        m.setMessageStatus(parseEnum(MessageStatus.class, d.get("messageStatus"))); 
        m.setMessageType(parseEnum(MessageType.class, d.get("messageType")));
        m.setEventTimestamp(parseInstant(d.get("eventTimestamp")));
        return m;
    }

    private static String extractAppId(List<Messages> list){ return list.isEmpty()?"": nz(list.get(0).getAppId()); }
    private static String extractAdminId(List<Messages> list){ return list.isEmpty()?"": nz(list.get(0).getAdminId()); }
    private static String extractDeviceId(List<Messages> list){ return list.isEmpty()?"": nz(list.get(0).getDeviceId()); }
    private static String asStr(Object o) { return o == null ? null : String.valueOf(o); }
    private static Integer asInt(Object o) { try { return o == null? null : Integer.valueOf(String.valueOf(o)); } catch(Exception e){ return null; } }
    private static Instant parseInstant(Object o) { try { return o==null? null: Instant.parse(String.valueOf(o)); } catch(Exception e){ return null; } }
    private static <E extends Enum<E>> E parseEnum(Class<E> e, Object o) { try { return o==null? null: Enum.valueOf(e, String.valueOf(o)); } catch(Exception ex){ return null; } }
    private static String nz(String s){ return s==null? "" : s; }
    // no external dedupe hashing in snapshot mode
}
