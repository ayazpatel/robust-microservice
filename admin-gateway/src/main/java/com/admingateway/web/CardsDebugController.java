package com.admingateway.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.admingateway.security.SessionValidator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/_debug/cards")
class CardsDebugController {

    private final StringRedisTemplate redis;
    private final SessionValidator sessionValidator;
    private final ObjectMapper om;

    CardsDebugController(StringRedisTemplate redis, SessionValidator sessionValidator, ObjectMapper om) {
        this.redis = redis; this.sessionValidator = sessionValidator; this.om = om;
    }

    @GetMapping("/snapshot")
    public ResponseEntity<Map<String,Object>> snapshot(@CookieValue(value="asid", required=false) String asid,
                                      @RequestHeader(value="X-Session-Id", required = false) String sidHeader,
                                      @RequestParam("deviceId") String deviceId) {
        String sid = (sidHeader!=null && !sidHeader.isBlank()) ? sidHeader : asid;
        var v = sessionValidator.validate(sid);
        if (!v.active()) return ResponseEntity.status(401).build();
        if (v.appId()==null || v.appId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_app_id",
                    "message", "Admin has no assigned app."
            ));
        }
        String key = "app:" + nz(v.appId()) + ":" + nz(v.adminId()) + ":" + nz(deviceId);
        Object raw = redis.opsForHash().get(key, "cards");
        Map<String,Object> out = new HashMap<>();
        out.put("key", key);
        if (raw == null) {
            out.put("present", false);
            return ResponseEntity.ok(out);
        }
        out.put("present", true);
        String json = String.valueOf(raw);
        out.put("bytes", json.length());
        try {
            List<Object> arr = om.readValue(json, new TypeReference<List<Object>>(){});
            out.put("arraySize", arr.size());
        } catch (Exception ignore) {
            out.put("arraySize", -1);
        }
        return ResponseEntity.ok(out);
    }

    private static String nz(String s){ return s==null?"":s; }
}
