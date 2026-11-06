package app.service.store;

import app.service.common.CallForwardReason;
import app.service.common.CallForwardServiceType;
import app.service.model.CallForward;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class RedisCallForwardStore implements CallForwardStore {
    private static final Logger log = LoggerFactory.getLogger(RedisCallForwardStore.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public RedisCallForwardStore(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis; this.om = om;
    }

    private static final CallForwardServiceType DEFAULT_SERVICE = CallForwardServiceType.VOICE;

    private String prefixKey(String appId, String adminId, String deviceId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":" + nz(deviceId);
    }

    @Override
    public void upsertSnapshot(String adminId, String deviceId, Instant collectedAt, List<CallForward> rules) {
        if (rules == null) return;
        int saved = 0;
        List<CallForward> normalized = new ArrayList<>();
        for (CallForward r : rules) {
            try {
                if (r == null) continue;
                r.setAdminId(adminId);
                r.setDeviceId(deviceId);
                if (r.getService() == null) r.setService(DEFAULT_SERVICE);
                Instant ca = collectedAt != null ? collectedAt : (r.getCollectedAt() != null ? r.getCollectedAt() : Instant.now());
                r.setCollectedAt(ca);
                if (r.getSubscriptionId() != null && r.getReason() != null) {
                    long id = computeId(adminId, deviceId, r.getSubscriptionId(), r.getReason(), r.getService());
                    r.setId(id);
                }
                normalized.add(r);
                saved++;
            } catch (Exception e) {
                log.error("Failed to normalize callforward", e);
            }
        }
        try {
            String key = prefixKey((normalized.isEmpty()? "" : Objects.toString(normalized.get(0).getAppId(), "")), adminId, deviceId);
            String json = om.writeValueAsString(normalized);
            redis.opsForHash().put(Objects.requireNonNull(key), "callforwarding", Objects.requireNonNull(json));
        } catch (Exception e) {
            log.error("Failed to write unified callforwarding snapshot", e);
        }
        log.info("callforward upsertSnapshot admin={} device={} saved={}", adminId, deviceId, saved);
    }

    @Override
    public List<CallForward> list(CallForwardQuery q) {
        String adminId = Objects.toString(q.getAdminId(), "");
        String appId = Objects.toString(q.getAppId(), "");
        String deviceId = q.getDeviceId();
        int page = Math.max(0, q.getPage());
        int size = Math.max(1, Math.min(200, q.getSize()));
        List<CallForward> out = new ArrayList<>();
        try {
            if (deviceId == null || deviceId.isBlank()) return out;
            String key = prefixKey(appId, adminId, deviceId);
            String json = (String) redis.opsForHash().get(Objects.requireNonNull(key), "callforwarding");
            if (json == null || json.isBlank()) return out;
            java.util.List<CallForward> all = om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<CallForward>>(){});
            // Filter
            List<CallForward> filtered = new ArrayList<>();
            for (CallForward cf : all) {
                if (!matchesFilters(cf.getSubscriptionId(), cf.getReason(), cf.getService(), q)) continue;
                filtered.add(cf);
            }
            // Sort desc by collectedAt
            filtered.sort((a,b) -> {
                long am = a.getCollectedAt()!=null? a.getCollectedAt().toEpochMilli() : 0L;
                long bm = b.getCollectedAt()!=null? b.getCollectedAt().toEpochMilli() : 0L;
                return Long.compare(bm, am);
            });
            int total = filtered.size();
            int start = Math.min(page * size, total);
            int end = Math.min(start + size, total);
            if (start < end) out.addAll(filtered.subList(start, end));
        } catch (Exception e) {
            log.error("Failed to list callforward", e);
        }
        return out;
    }


    private boolean matchesFilters(Integer subId, CallForwardReason reason, CallForwardServiceType svc, CallForwardQuery q) {
        if (q.getSubscriptionId() != null && !q.getSubscriptionId().equals(subId)) return false;
        if (q.getReason() != null && q.getReason() != reason) return false;
        return !(q.getService() != null && q.getService() != svc);
    }

    private long computeId(String adminId, String deviceId, Integer subId, CallForwardReason reason, CallForwardServiceType svc) {
        long h = 1125899906842597L;
        String s = Objects.toString(adminId, "") + '|' + Objects.toString(deviceId, "") + '|' + Objects.toString(subId, "") + '|' + (reason != null ? reason.name() : "") + '|' + (svc != null ? svc.name() : DEFAULT_SERVICE.name());
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return h < 0 ? -h : h;
    }

    private static String nz(String s){ return s==null? "" : s; }
}
