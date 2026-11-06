package app.service.store;

import app.service.common.CallType;
import app.service.model.CallLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class RedisCallLogStore implements CallLogStore {
    private static final Logger log = LoggerFactory.getLogger(RedisCallLogStore.class);
    // Snapshot-only: no external dedupe keys
    private static final String F_PLATFORM_ID = "platformId";
    private static final String F_APP_ID = "appId";
    private static final String F_ADMIN_ID = "adminId";
    private static final String F_DEVICE_ID = "deviceId";
    private static final String F_NUMBER = "number";
    private static final String F_TYPE = "type";
    private static final String F_DURATION = "durationSec";
    private static final String F_EVENT_TS = "eventTimestamp";

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public RedisCallLogStore(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis; this.om = om;
    }

    private String prefixKey(String appId, String adminId, String deviceId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":" + nz(deviceId);
    }
    // No dedupe key helpers in snapshot mode

    @Override
    public void saveBatch(String adminId, String deviceId, List<CallLog> batch) {
        if (batch == null || batch.isEmpty()) return;
    // Group by unified key and replace per device
        Map<String, List<CallLog>> byKey = new LinkedHashMap<>();
        for (CallLog c : batch) {
            String appId = Objects.toString(c.getAppId(), "");
            String key = prefixKey(appId, adminId, deviceId);
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }
    int saved = 0;
        for (Map.Entry<String, List<CallLog>> e : byKey.entrySet()) {
            String key = e.getKey();
            List<CallLog> list = e.getValue();
            List<Map<String, Object>> current = new ArrayList<>(list.size());
            for (CallLog c : list) {
                String appId = Objects.toString(c.getAppId(), "");
                // Ensure metadata
                c.setAdminId(adminId);
                c.setDeviceId(deviceId);

                // Compute stable id
                long ridLong = computeId(adminId, deviceId, c);
                if (c.getId() == null || c.getId() == 0L) c.setId(ridLong);

                Map<String,Object> row = new LinkedHashMap<>();
                row.put("id", c.getId());
                if (c.getPlatformId()!=null) row.put(F_PLATFORM_ID, c.getPlatformId());
                row.put(F_APP_ID, appId);
                row.put(F_ADMIN_ID, adminId);
                row.put(F_DEVICE_ID, deviceId);
                row.put(F_NUMBER, c.getNumber());
                row.put(F_TYPE, c.getType()!=null? c.getType().name(): null);
                row.put(F_DURATION, c.getDurationSec());
                row.put(F_EVENT_TS, c.getEventTimestamp()!=null? c.getEventTimestamp().toString() : Instant.now().toString());
                current.add(row);
                saved++;
            }

            // Sort desc by timestamp
            current.sort((a,b) -> {
                Instant ia = parseInstant(a.get(F_EVENT_TS));
                Instant ib = parseInstant(b.get(F_EVENT_TS));
                long am = ia!=null? ia.toEpochMilli() : 0L;
                long bm = ib!=null? ib.toEpochMilli() : 0L;
                return Long.compare(bm, am);
            });

            try {
                String json = om.writeValueAsString(current);
                redis.opsForHash().put(key, "calllogs", json);
            } catch (Exception ex) {
                log.error("Failed to write unified calllogs array", ex);
            }
        }
    log.info("calllog saveBatch admin={} device={} saved={} skipped={}", adminId, deviceId, saved, 0);
    }

    @Override
    public List<CallLog> list(CallLogQuery q) {
        String adminId = Objects.toString(q.getAdminId(), "");
        String appId = Objects.toString(q.getAppId(), "");
        String deviceId = q.getDeviceId();
        int page = Math.max(0, q.getPage());
        int size = Math.max(1, Math.min(200, q.getSize()));
        List<CallLog> out = new ArrayList<>();
        try {
            if (deviceId == null || deviceId.isBlank()) return out; // unified storage is per-device
            String key = prefixKey(appId, adminId, deviceId);
            String json = (String) redis.opsForHash().get(key, "calllogs");
            if (json == null || json.isBlank()) return out;
            java.util.List<java.util.Map<String,Object>> list = om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String,Object>>>(){});
            // Filter
            List<CallLog> filtered = new ArrayList<>();
            for (java.util.Map<String,Object> m : list) {
                CallLog c = fromMap(m);
                if (!filter(c, q)) continue;
                filtered.add(c);
            }
            // Already stored sorted desc, but ensure ordering
            filtered.sort((a,b) -> {
                long am = a.getEventTimestamp()!=null? a.getEventTimestamp().toEpochMilli() : 0L;
                long bm = b.getEventTimestamp()!=null? b.getEventTimestamp().toEpochMilli() : 0L;
                return Long.compare(bm, am);
            });
            int total = filtered.size();
            int start = Math.min(page * size, total);
            int end = Math.min(start + size, total);
            if (start < end) out.addAll(filtered.subList(start, end));
        } catch (Exception e) {
            log.error("Failed to list calllogs", e);
        }
        return out;
    }

    private CallLog fromMap(Map<String,Object> m) {
        CallLog c = new CallLog();
        c.setId(asLong(m.get("id")));
        c.setPlatformId(asLong(m.get(F_PLATFORM_ID)));
        c.setAppId(asStr(m.get(F_APP_ID)));
        c.setAdminId(asStr(m.get(F_ADMIN_ID)));
        c.setDeviceId(asStr(m.get(F_DEVICE_ID)));
        c.setNumber(asStr(m.get(F_NUMBER)));
        c.setType(parseEnum(CallType.class, m.get(F_TYPE)));
        c.setDurationSec(asInt(m.get(F_DURATION)));
        c.setEventTimestamp(parseInstant(m.get(F_EVENT_TS)));
        return c;
    }

    private boolean filter(CallLog c, CallLogQuery q) {
        if (c == null) return false;
        if (q.getNumber() != null && !q.getNumber().isBlank()) {
            String n = c.getNumber(); if (n == null || !n.equals(q.getNumber())) return false;
        }
        if (q.getType() != null && c.getType() != q.getType()) return false;
        Instant ts = c.getEventTimestamp();
        if (q.getFrom() != null && (ts == null || ts.isBefore(q.getFrom()))) return false;
        return !(q.getTo() != null && (ts == null || ts.isAfter(q.getTo())));
    }

    private long computeId(String adminId, String deviceId, CallLog c) {
        // prefer platformId if present
        if (c.getPlatformId() != null) return Math.abs(Objects.hash(adminId, deviceId, c.getPlatformId()));
        long h = 1125899906842597L;
        String num = Objects.toString(c.getNumber(), "");
        String tp = c.getType() != null ? c.getType().name() : "";
        String dur = Objects.toString(c.getDurationSec(), "");
        String ts = String.valueOf(c.getEventTimestamp() != null ? c.getEventTimestamp().toEpochMilli() : 0);
        String s = adminId + '|' + deviceId + '|' + num + '|' + ts + '|' + tp + '|' + dur;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return h < 0 ? -h : h;
    }

    private static String nz(String s){ return s==null? "" : s; }
    private static String asStr(Object o){ return o==null? null : String.valueOf(o); }
    private static Integer asInt(Object o){ try { return o==null? null : Integer.valueOf(String.valueOf(o)); } catch(Exception e){ return null; } }
    private static Long asLong(Object o){ try { return o==null? null : Long.valueOf(String.valueOf(o)); } catch(Exception e){ return null; } }
    private static Instant parseInstant(Object o){ try { return o==null? null : Instant.parse(String.valueOf(o)); } catch(Exception e){ return null; } }
    private static <E extends Enum<E>> E parseEnum(Class<E> e, Object o){ try { return o==null? null : Enum.valueOf(e, String.valueOf(o)); } catch(Exception ex){ return null; } }
}
