package com.admingateway.web;

import com.admingateway.grpc.ContactsGrpcClient;
import com.admingateway.grpc.contacts.ListContactsRequest;
import com.admingateway.security.SessionValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/contacts")
public class ContactsRestProxyController {
    private final SessionValidator sessionValidator;
    private final ContactsGrpcClient grpc;

    public ContactsRestProxyController(SessionValidator sessionValidator, ContactsGrpcClient grpc){ this.sessionValidator=sessionValidator; this.grpc = grpc; }

    @GetMapping
    public ResponseEntity<Map<String,Object>> list(@CookieValue(value="asid", required=false) String asid,
                                  @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
                                  @RequestParam(value="deviceId", required=false) String deviceId,
                                  @RequestParam(value="q", required=false) String q,
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
        var req = buildContactsRequest(v, new ContactsFilters(deviceId, q), page, size);
        var resp = grpc.list(req);
        List<Map<String,Object>> items = resp.getItemsList().stream().map(it -> {
            Map<String,Object> d = new HashMap<>();
            d.put("id", it.getId());
            d.put("deviceId", it.getDeviceId());
            d.put("lookupKey", it.getLookupKey());
            d.put("displayName", it.getDisplayName());
            d.put("collectedAt", Instant.ofEpochMilli(it.getCollectedAtMs()));
            d.put("numbers", it.getNumbersList());
            return d;
        }).toList();
        Map<String,Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", resp.getPage());
        out.put("size", resp.getSize());
        out.put("count", resp.getCount());
        return ResponseEntity.ok(out);
    }

    private ListContactsRequest buildContactsRequest(SessionValidator.Result session,
                                                     ContactsFilters filters,
                                                     Integer page,
                                                     Integer size) {
        boolean explicitUnpaged = size != null && size <= 0;
        boolean unpaged = (page == null && size == null) || explicitUnpaged;
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 50 : Math.max(1, Math.min(200, size));
        return ListContactsRequest.newBuilder()
                .setAdminId(session.adminId()==null?"":session.adminId())
                .setAppId(session.appId()==null?"":session.appId())
                .setDeviceId(filters.deviceId()==null?"":filters.deviceId())
                .setQ(filters.q()==null?"":filters.q())
                .setPage(unpaged ? 0 : safePage)
                .setSize(unpaged ? 0 : safeSize)
                .build();
    }

    private record ContactsFilters(String deviceId, String q) {}
}

