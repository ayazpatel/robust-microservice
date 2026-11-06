package app.service.store;

import app.service.model.TelemetryRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class RedisTelemetryStore implements TelemetryStore {

    private static final Logger log = LoggerFactory.getLogger(RedisTelemetryStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public RedisTelemetryStore(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis; this.om = om;
    }

    private String prefixKey(String appId, String adminId, String deviceId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":" + nz(deviceId);
    }
    private String serviceHashKey(String appId, String adminId) {
        // Cross-device index: one HASH per service with field=deviceId -> telemetry JSON
    return "app:" + nz(appId) + ":" + nz(adminId) + ":telemetry";
    }
    private String serviceZsetKey(String appId, String adminId) {
        // Sorted by latest telemetry event timestamp for pagination
    return "app:" + nz(appId) + ":" + nz(adminId) + ":telemetry:by_ts";
    }
    private static final String APPS_INDEX_KEY = "app:_apps";
    private String adminsIndexKey(String appId) { return "app:" + nz(appId) + ":_admins"; }
    private String devicesIndexKey(String appId, String adminId) { return "app:" + nz(appId) + ":" + nz(adminId) + ":_devices"; }

    @Override
    public void upsertSnapshot(TelemetryRecord rec) {
        try {
            String adminId = Objects.toString(rec.getAdminId(), "");
            String appId = Objects.toString(rec.getAppId(), "");
            String deviceId = Objects.toString(rec.getDeviceId(), "");
            String key = prefixKey(appId, adminId, deviceId);
            String json = om.writeValueAsString(rec);
            // SET snapshot under hash field 'telemetry' (replace semantics)
            redis.opsForHash().put(Objects.requireNonNull(key), "telemetry", Objects.requireNonNull(json));

            // Secondary indexes for efficient cross-device queries
            // 1) Per-service HASH: field=deviceId -> telemetry JSON
            String svcHash = serviceHashKey(appId, adminId);
            redis.opsForHash().put(svcHash, deviceId, json);
            // 2) ZSET ordered by event timestamp (for pagination)
            long ts = rec.getEventTs() != null ? rec.getEventTs().toEpochMilli() : System.currentTimeMillis();
            String svcZ = serviceZsetKey(appId, adminId);
            redis.opsForZSet().add(svcZ, deviceId, ts);
            // 3) Simple hierarchy indexes (sets) for tree/browse
            redis.opsForSet().add(APPS_INDEX_KEY, appId);
            redis.opsForSet().add(adminsIndexKey(appId), adminId);
            redis.opsForSet().add(devicesIndexKey(appId, adminId), deviceId);
        } catch (Exception e) {
            log.error("Failed to upsert telemetry snapshot", e);
        }
    }

    @Override
    public List<TelemetryRecord> list(TelemetryQuery q) {
    String adminId = Objects.toString(q.getAdminId(), "");
    String appId = Objects.toString(q.getAppId(), "");
        List<TelemetryRecord> out = new ArrayList<>();

        try {
            if (q.getDeviceId() != null && !q.getDeviceId().isBlank()) {
                return listSingleDevice(appId, adminId, q, out);
            }
            // Cross-device listing: use ZSET for ordering + HASH for payloads
            String svcZ = serviceZsetKey(appId, adminId);
            String svcHash = serviceHashKey(appId, adminId);
            double min = q.getFrom() != null ? (double) q.getFrom().toEpochMilli() : Double.NEGATIVE_INFINITY;
            double max = q.getTo() != null ? (double) q.getTo().toEpochMilli() : Double.POSITIVE_INFINITY;
            int page = Math.max(0, q.getPage());
            boolean unpaged = q.isUnpaged();
            java.util.Set<String> deviceIds;
            if (unpaged) {
                deviceIds = redis.opsForZSet().reverseRangeByScore(svcZ, min, max);
            } else {
                int size = Math.max(1, Math.min(q.getSize(), 200));
                long offset = (long) page * size;
                deviceIds = redis.opsForZSet().reverseRangeByScore(svcZ, min, max, offset, size);
            }
            if (deviceIds == null || deviceIds.isEmpty()) return out;
            java.util.List<Object> fields = new java.util.ArrayList<>(deviceIds);
            java.util.List<Object> vals = redis.opsForHash().multiGet(svcHash, fields);
            if (vals == null) return out;
            for (Object val : vals) {
                TelemetryRecord r = parseTelemetry(String.valueOf(val));
                if (r != null && within(r, q.getFrom(), q.getTo())) out.add(r);
            }
            return out;
        } catch (Exception e) {
            log.error("Failed to list telemetry", e);
            return out;
        }
    }

    private List<TelemetryRecord> listSingleDevice(String appId, String adminId, TelemetryQuery q, List<TelemetryRecord> out) {
        try {
            String key = prefixKey(appId, adminId, q.getDeviceId());
            String json = (String) redis.opsForHash().get(Objects.requireNonNull(key), "telemetry");
            if (json != null) {
                TelemetryRecord r = om.readValue(json.getBytes(StandardCharsets.UTF_8), TelemetryRecord.class);
                if (within(r, q.getFrom(), q.getTo())) out.add(r);
            }
        } catch (Exception e) {
            log.error("Failed to list single device", e);
        }
        return out;
    }

    // Cross-device listing helpers removed in unified storage model

    private boolean within(TelemetryRecord r, Instant from, Instant to) {
        if (r == null || r.getEventTs() == null) return false;
        Instant ts = r.getEventTs();
        if (from != null && ts.isBefore(from)) return false;
        return to == null || !ts.isAfter(to);
    }

    private static String nz(String s){ return s==null? "" : s; }

    private TelemetryRecord parseTelemetry(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return om.readValue(json, TelemetryRecord.class);
        } catch (Exception e) {
            log.warn("Failed to parse telemetry JSON", e);
            return null;
        }
    }
}
