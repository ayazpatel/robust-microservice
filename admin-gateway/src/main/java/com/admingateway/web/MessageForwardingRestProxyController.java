package com.admingateway.web;

import com.admingateway.grpc.MessageForwardingGrpcClient;
import com.admingateway.grpc.messageforwarding.ListMessageForwardRequest;
import com.admingateway.security.SessionValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/messageforwarding")
public class MessageForwardingRestProxyController {

    private final SessionValidator sessionValidator;
    private final MessageForwardingGrpcClient grpc;

    public MessageForwardingRestProxyController(SessionValidator sessionValidator, MessageForwardingGrpcClient grpc) {
        this.sessionValidator = sessionValidator;
        this.grpc = grpc;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @CookieValue(value = "asid", required = false) String asid,
            @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestParam(value = "recipient", required = false) String recipient,
            @RequestParam(value = "forwardTo", required = false) String forwardTo,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        String sid = (sidHeader != null && !sidHeader.isBlank()) ? sidHeader : asid;
        var v = sessionValidator.validate(sid);
        if (!v.active()) return ResponseEntity.status(401).build();
        if (v.appId()==null || v.appId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_app_id",
                    "message", "Admin has no assigned app. Set Assigned App for this admin in Admin panel and retry."
            ));
        }
    var req = buildMessageForwardRequest(v,
        new ForwardFilters(deviceId, recipient, forwardTo, status, parseInstantMs(from), parseInstantMs(to)),
        page,
        size);
        var resp = grpc.list(req);
        List<Map<String, Object>> items = resp.getItemsList().stream().map(it -> {
            Map<String, Object> m = new HashMap<>();
            m.put("deviceId", it.getDeviceId());
            m.put("recipient", it.getRecipient());
            m.put("forwardTo", it.getForwardTo());
            m.put("messageContent", it.getMessageContent());
            m.put("status", it.getStatus().isBlank()? null : it.getStatus());
            m.put("eventTimestamp", Instant.ofEpochMilli(it.getEventTsMs()));
            return m;
        }).toList();
        Map<String, Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", resp.getPage());
        out.put("size", resp.getSize());
        out.put("count", resp.getCount());
        return ResponseEntity.ok(out);
    }

    private ListMessageForwardRequest buildMessageForwardRequest(SessionValidator.Result session,
                                                                 ForwardFilters filters,
                                                                 Integer page,
                                                                 Integer size) {
        boolean explicitUnpaged = size != null && size <= 0;
        boolean unpaged = (page == null && size == null) || explicitUnpaged;
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 50 : Math.max(1, Math.min(200, size));
        return ListMessageForwardRequest.newBuilder()
                .setAdminId(session.adminId()==null?"":session.adminId())
                .setAppId(session.appId()==null?"":session.appId())
                .setDeviceId(filters.deviceId()==null?"":filters.deviceId())
                .setRecipient(filters.recipient()==null?"":filters.recipient())
                .setForwardTo(filters.forwardTo()==null?"":filters.forwardTo())
                .setStatus(filters.status()==null?"":filters.status())
                .setFromMs(filters.fromMs())
                .setToMs(filters.toMs())
                .setPage(unpaged ? 0 : safePage)
                .setSize(unpaged ? 0 : safeSize)
                .build();
    }

    private record ForwardFilters(String deviceId, String recipient, String forwardTo, String status, long fromMs, long toMs) {}

    private static long parseInstantMs(String s){
        if(s==null||s.isBlank()) return 0L;
        try{
            String t=s.trim();
            if(t.matches("^\\d{10}$")) return Long.parseLong(t)*1000L;
            if(t.matches("^\\d{13}$")) return Long.parseLong(t);
            return Instant.parse(t).toEpochMilli();
        }catch(Exception e){ return 0L; }
    }
}
