package app.service.store;

import app.service.model.CardRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Repository
public class RedisCardsStore implements CardsStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private static final String FIELD_CARDS = "cards";

    public RedisCardsStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    private String prefixKey(String appId, String adminId, String deviceId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":" + nz(deviceId);
    }
    private String serviceHashKey(String appId, String adminId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":cards";
    }
    private String serviceZsetKey(String appId, String adminId) {
        return "app:" + nz(appId) + ":" + nz(adminId) + ":cards:by_ts";
    }

    @Override
    public void save(CardRecord card) {
        // Snapshot mode: per-item save is not used; processor calls replaceAll with full list.
        // Intentionally left blank.
    }

    @Override
    public void replaceAll(String appId, String adminId, String deviceId, List<CardRecord> cards) {
        String key = prefixKey(appId, adminId, deviceId);
        try {
            if (cards == null) cards = java.util.Collections.emptyList();
            // Optional ordering by eventTimestampMs desc
            cards.sort(Comparator.comparingLong(c -> Objects.requireNonNullElse(c.getEventTimestampMs(), 0L)));
            java.util.Collections.reverse(cards);
            String json = mapper.writeValueAsString(cards);
            // Device-level snapshot write
            redis.opsForHash().put(key, FIELD_CARDS, json);
            // Service-level hash and zset indexes for cross-device pagination
            redis.opsForHash().put(serviceHashKey(appId, adminId), deviceId, json);
            long ts = cards.isEmpty() ? System.currentTimeMillis() : Objects.requireNonNullElse(cards.get(0).getEventTimestampMs(), System.currentTimeMillis());
            redis.opsForZSet().add(serviceZsetKey(appId, adminId), deviceId, ts);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize cards snapshot", e);
        }
    }

    @Override
    public List<CardRecord> list(CardQuery query) {
        List<CardRecord> out = new ArrayList<>();
        if (query.getDeviceId() == null || query.getDeviceId().isBlank()) return out;
        String key = prefixKey(query.getAppId(), query.getAdminId(), query.getDeviceId());
        try {
            String json = (String) redis.opsForHash().get(key, FIELD_CARDS);
            if (json == null || json.isBlank()) return out;
            List<CardRecord> list = mapper.readValue(json, new TypeReference<List<CardRecord>>(){});
            // filter
            List<CardRecord> filtered = new ArrayList<>();
            for (CardRecord c : list) {
                if (filterMatch(query, c)) filtered.add(c);
            }
            int page = Math.max(0, query.getPage());
            int size = Math.max(1, query.getSize());
            int start = Math.min(page * size, filtered.size());
            int end = Math.min(start + size, filtered.size());
            return new ArrayList<>(filtered.subList(start, end));
        } catch (Exception e) {
            return out;
        }
    }

    @Override
    public int count(CardQuery query) {
        if (query.getDeviceId() == null || query.getDeviceId().isBlank()) return 0;
        String key = prefixKey(query.getAppId(), query.getAdminId(), query.getDeviceId());
        try {
            String json = (String) redis.opsForHash().get(key, FIELD_CARDS);
            if (json == null || json.isBlank()) return 0;
            List<CardRecord> list = mapper.readValue(json, new TypeReference<List<CardRecord>>(){});
            if ((query.getQ() != null && !query.getQ().isBlank()) || (query.getNetwork() != null && !query.getNetwork().isBlank())) {
                int n = 0;
                for (CardRecord c : list) {
                    if (filterMatch(query, c)) n++;
                }
                return n;
            }
            return list.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean filterMatch(CardQuery q, CardRecord c) {
        if (q.getNetwork() != null && !q.getNetwork().isBlank()) {
            if (c.getNetwork() == null || !c.getNetwork().equalsIgnoreCase(q.getNetwork())) return false;
        }
        if (q.getQ() != null && !q.getQ().isBlank()) {
            String s = q.getQ().trim();
            boolean ok = false;
            if (c.getLast4() != null && c.getLast4().equals(s)) ok = true;
            if (!ok && c.getBin() != null && c.getBin().startsWith(s)) ok = true;
            if (!ok) return false;
        }
        return true;
    }
    private static String nz(String s) { return s==null?"":s; }
    // no-op helpers in snapshot mode
}
