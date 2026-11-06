package app.service.web;

import app.service.common.CallType;
import app.service.model.CallLog;
import app.service.store.CallLogQuery;
import app.service.store.CallLogStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/call-logs")
public class CallLogsApiController {
    private final CallLogStore store;
    private final SessionValidator sessionValidator;

    public CallLogsApiController(CallLogStore store, SessionValidator sessionValidator) {
        this.store = store;
        this.sessionValidator = sessionValidator;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@CookieValue(value = "asid", required = false) String asid,
            @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestParam(value = "number", required = false) String number,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        String sid = sidHeader != null && !sidHeader.isBlank() ? sidHeader : asid;
        var v = sessionValidator.validate(sid);
        if (!v.active()) return ResponseEntity.status(401).build();

        int ps = Math.max(1, Math.min(size, 200));
        CallType tp = parseType(type);
        Instant fromTs = parseInstantFlexible(from);
        Instant toTs = parseInstantFlexible(to);

        CallLogQuery q = new CallLogQuery();
        q.setAdminId(v.adminId());
        q.setDeviceId(deviceId);
        q.setNumber(number);
        q.setType(tp);
        q.setFrom(fromTs);
        q.setTo(toTs);
        q.setPage(Math.max(0, page));
        q.setSize(ps);

        List<CallLog> items = store.list(q);
        Map<String, Object> out = new HashMap<>();
        out.put("items", items.stream().map(CallLogsApiController::toDto).toList());
        out.put("page", page);
        out.put("size", ps);
        out.put("count", items.size());
    return ResponseEntity.ok(out);
    }

    private static Map<String, Object> toDto(CallLog c) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", c.getId());
        d.put("deviceId", c.getDeviceId());
        d.put("number", c.getNumber());
        d.put("type", c.getType() == null ? null : c.getType().name());
        d.put("durationSec", c.getDurationSec());
        d.put("eventTimestamp", c.getEventTimestamp());
        return d;
    }

    private static CallType parseType(String s) {
        if (s == null || s.isBlank()) return null;
        try { return CallType.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { return null; }
    }

    private static Instant parseInstantFlexible(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            // Try ISO-8601 first
            return Instant.parse(s.trim());
    } catch (Exception ignore) { /* ignore invalid ISO-8601 */ }
        try {
            // Try epoch millis
            long v = Long.parseLong(s.trim());
            // Heuristic: treat 10-digit as seconds
            if (s.trim().length() == 10) return Instant.ofEpochSecond(v);
            return Instant.ofEpochMilli(v);
        } catch (Exception ignore) { /* ignore invalid epoch */ }
        return null;
    }
}
