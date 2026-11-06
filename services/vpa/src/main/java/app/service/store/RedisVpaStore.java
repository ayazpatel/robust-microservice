package app.service.store;

import app.service.model.VpaRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class RedisVpaStore implements VpaStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper om;
    private static final String FIELD_VPA = "vpa";

    public RedisVpaStore(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis; this.om = om;
    }

    private String prefixKey(String appId, String adminId, String deviceId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":" + nz(deviceId);
    }
    private String serviceHashKey(String appId, String adminId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":vpa";
    }
    private String serviceZsetKey(String appId, String adminId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":vpa:by_ts";
    }

    @Override
    public void save(VpaRecord rec) {
        // Snapshot mode: not used; processor will call replaceAll
    }

    @Override
    public void replaceAll(String appId, String adminId, String deviceId, List<VpaRecord> list) {
        String key = prefixKey(appId, adminId, deviceId);
        try {
            if (list == null) list = java.util.Collections.emptyList();
            list.sort(java.util.Comparator.comparingLong(v -> Objects.requireNonNullElse(v.getEventTimestampMs(), 0L)));
            java.util.Collections.reverse(list);
            String json = om.writeValueAsString(list);
            // Device-level snapshot
            redis.opsForHash().put(key, FIELD_VPA, json);
            // Service-level hash and zset for cross-device pagination
            redis.opsForHash().put(serviceHashKey(appId, adminId), deviceId, json);
            long ts = list.isEmpty() ? System.currentTimeMillis() : Objects.requireNonNullElse(list.get(0).getEventTimestampMs(), System.currentTimeMillis());
            redis.opsForZSet().add(serviceZsetKey(appId, adminId), deviceId, ts);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize vpa snapshot", e);
        }
    }

    @Override
    public List<VpaRecord> list(VpaQuery q) {
        List<VpaRecord> out = new ArrayList<>();
        if (q.getDeviceId()==null || q.getDeviceId().isBlank()) return out;
        String key = prefixKey(q.getAppId(), q.getAdminId(), q.getDeviceId());
        try {
            String json = (String) redis.opsForHash().get(key, FIELD_VPA);
            if (json == null || json.isBlank()) return out;
            List<VpaRecord> list = om.readValue(json, new TypeReference<List<VpaRecord>>(){});
            List<VpaRecord> filtered = new ArrayList<>();
            for (VpaRecord v : list) {
                if (filterMatch(q, v)) filtered.add(v);
            }
            int page = Math.max(0, q.getPage());
            int size = Math.max(1, q.getSize());
            int start = Math.min(page * size, filtered.size());
            int end = Math.min(start + size, filtered.size());
            return new ArrayList<>(filtered.subList(start, end));
        } catch (Exception e) { return out; }
    }

    @Override
    public int count(VpaQuery q) {
        if (q.getDeviceId()==null || q.getDeviceId().isBlank()) return 0;
        String key = prefixKey(q.getAppId(), q.getAdminId(), q.getDeviceId());
        try {
            String json = (String) redis.opsForHash().get(key, FIELD_VPA);
            if (json == null || json.isBlank()) return 0;
            List<VpaRecord> list = om.readValue(json, new TypeReference<List<VpaRecord>>(){});
            if (q.getQ()!=null && !q.getQ().isBlank()) {
                int n=0;
                for (VpaRecord v : list) {
                    if (filterMatch(q, v)) n++;
                }
                return n;
            }
            return list.size();
        } catch (Exception e) { return 0; }
    }

    private boolean filterMatch(VpaQuery q, VpaRecord v) {
        if (q.getQ()==null || q.getQ().isBlank()) return true;
        String s = q.getQ().trim().toLowerCase();
        if (v.getUpiId()!=null && v.getUpiId().toLowerCase().contains(s)) return true;
        if (v.getPhoneNumber()!=null && v.getPhoneNumber().toLowerCase().contains(s)) return true;
        return false;
    }

    private static String nz(String s){ return s==null?"":s; }
}
