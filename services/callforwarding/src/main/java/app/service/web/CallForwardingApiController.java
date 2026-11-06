package app.service.web;

import app.service.common.CallForwardReason;
import app.service.common.CallForwardServiceType;
import app.service.model.CallForward;
import app.service.store.CallForwardQuery;
import app.service.store.CallForwardStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/call-forward")
public class CallForwardingApiController {
    private final CallForwardStore store;
    private final SessionValidator sessionValidator;

    public CallForwardingApiController(CallForwardStore store, SessionValidator sessionValidator) {
        this.store = store;
        this.sessionValidator = sessionValidator;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@CookieValue(value = "asid", required = false) String asid,
            @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestParam(value = "subscriptionId", required = false) Integer subscriptionId,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        String sid = sidHeader != null && !sidHeader.isBlank() ? sidHeader : asid;
        var v = sessionValidator.validate(sid);
        if (!v.active())
            return ResponseEntity.status(401).build();
        int ps = Math.max(1, Math.min(size, 200));
        CallForwardQuery q = new CallForwardQuery();
        q.setAdminId(v.adminId());
        q.setDeviceId(deviceId);
        q.setSubscriptionId(subscriptionId);
        q.setReason(parseReason(reason));
        q.setService(parseService(service));
        q.setPage(Math.max(0, page));
        q.setSize(ps);
        List<CallForward> rows = store.list(q);
        List<Map<String, Object>> items = rows.stream().map(CallForwardingApiController::toDto).toList();
        Map<String, Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", page);
        out.put("size", ps);
        out.put("count", items.size());
        return ResponseEntity.ok(out);
    }

    private static Map<String, Object> toDto(CallForward c) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", c.getId());
        d.put("deviceId", c.getDeviceId());
        d.put("subscriptionId", c.getSubscriptionId());
        d.put("simSlotIndex", c.getSimSlotIndex());
        d.put("carrierName", c.getCarrierName());
        d.put("reason", c.getReason() == null ? null : c.getReason().name());
        d.put("enabled", c.isEnabled());
        d.put("forwardTo", c.getForwardTo());
        d.put("timeoutSeconds", c.getTimeoutSeconds());
        d.put("service", c.getService() == null ? null : c.getService().name());
        d.put("isSetByAdmin", c.getIsSetByAdmin());
        d.put("collectedAt", c.getCollectedAt());
        d.put("updatedAt", c.getUpdatedAt());
        return d;
    }

    private static CallForwardReason parseReason(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return CallForwardReason.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    private static CallForwardServiceType parseService(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return CallForwardServiceType.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
