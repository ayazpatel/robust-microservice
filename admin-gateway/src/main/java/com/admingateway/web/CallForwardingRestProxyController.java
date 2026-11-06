package com.admingateway.web;

import com.admingateway.grpc.CallForwardingGrpcClient;
import com.admingateway.grpc.callforwarding.ListCallForwardRequest;
import com.admingateway.security.SessionValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/callforwarding")
public class CallForwardingRestProxyController {
    private final SessionValidator sessionValidator;
    private final CallForwardingGrpcClient grpcClient;

    public CallForwardingRestProxyController(SessionValidator sessionValidator, CallForwardingGrpcClient grpcClient){
        this.sessionValidator=sessionValidator; this.grpcClient=grpcClient;
    }

    @GetMapping
    public ResponseEntity<Map<String,Object>> list(@CookieValue(value="asid", required=false) String asid,
                                  @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
                                  @RequestParam(value="deviceId", required=false) String deviceId,
                                  @RequestParam(value="subscriptionId", required=false) Integer subscriptionId,
                                  @RequestParam(value="reason", required=false) String reason,
                                  @RequestParam(value="service", required=false) String service,
                                  @RequestParam(value="page", required = false) Integer page,
                                  @RequestParam(value="size", required = false) Integer size){
        String sid = (sidHeader != null && !sidHeader.isBlank()) ? sidHeader : asid;
        var v=sessionValidator.validate(sid); if(!v.active()) return ResponseEntity.status(401).build();
        // Consistent guard: if admin has no assigned app, return 400 with clear message
        if (v.appId()==null || v.appId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "missing_app_id",
                "message", "Admin has no assigned app. Set Assigned App for this admin in Admin panel and retry."
            ));
        }

        var req = buildCallForwardRequest(v, new CallForwardFilters(deviceId, subscriptionId, reason, service), page, size);

        var resp = grpcClient.list(req);

        Map<String,Object> out = new HashMap<>();
        out.put("page", resp.getPage());
        out.put("size", resp.getSize());
        out.put("count", resp.getCount());
        // Map proto items to simple maps for JSON response
        var items = resp.getItemsList().stream().map(it -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", Long.toUnsignedString(it.getId()));
            m.put("deviceId", it.getDeviceId());
            m.put("subscriptionId", it.getSubscriptionId());
            m.put("simSlotIndex", it.getSimSlotIndex());
            m.put("carrierName", it.getCarrierName());
            m.put("reason", it.getReason());
            m.put("enabled", it.getEnabled());
            m.put("forwardTo", it.getForwardTo());
            m.put("timeoutSeconds", it.getTimeoutSeconds());
            m.put("service", it.getService());
            m.put("isSetByAdmin", it.getIsSetByAdmin());
            m.put("collectedAt", java.time.Instant.ofEpochMilli(it.getCollectedAtMs()));
            return m;
        }).toList();
        out.put("items", items);
        return ResponseEntity.ok(out);
    }

    private ListCallForwardRequest buildCallForwardRequest(SessionValidator.Result session,
                                                           CallForwardFilters filters,
                                                           Integer page,
                                                           Integer size) {
        boolean explicitUnpaged = size != null && size <= 0;
        boolean unpaged = (page == null && size == null) || explicitUnpaged;
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 50 : Math.max(1, Math.min(200, size));
        var builder = ListCallForwardRequest.newBuilder()
                .setAdminId(session.adminId()==null?"":session.adminId())
                .setAppId(session.appId()==null?"":session.appId())
                .setDeviceId(filters.deviceId()==null?"":filters.deviceId())
                .setPage(unpaged ? 0 : safePage)
                .setSize(unpaged ? 0 : safeSize);
        if (filters.subscriptionId() != null) builder.setSubscriptionId(filters.subscriptionId());
        if (filters.reason() != null) builder.setReason(filters.reason());
        if (filters.service() != null) builder.setService(filters.service());
        return builder.build();
    }

    private record CallForwardFilters(String deviceId, Integer subscriptionId, String reason, String service) {}
}

