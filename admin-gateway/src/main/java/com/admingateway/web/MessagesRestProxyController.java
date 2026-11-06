package com.admingateway.web;

import com.admingateway.grpc.MessagesGrpcClient;
import com.admingateway.grpc.messages.ListMessagesRequest;
import com.admingateway.security.SessionValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/messages")
public class MessagesRestProxyController {
    private final SessionValidator sessionValidator;
    private final MessagesGrpcClient grpc;

    public MessagesRestProxyController(SessionValidator sessionValidator, MessagesGrpcClient grpc){ this.sessionValidator=sessionValidator; this.grpc = grpc; }

    @GetMapping
    public ResponseEntity<Map<String,Object>> list(@CookieValue(value="asid", required=false) String asid,
                                  @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
                                  @RequestParam(value="deviceId", required=false) String deviceId,
                                  @RequestParam(value="recipient", required=false) String recipient,
                                  @RequestParam(value="status", required=false) String status,
                                  @RequestParam(value="type", required=false) String type,
                                  @RequestParam(value="from", required=false) String from,
                                  @RequestParam(value="to", required=false) String to,
                  @RequestParam(value="page", required = false) Integer page,
                  @RequestParam(value="size", required = false) Integer size){
        String sid = (sidHeader != null && !sidHeader.isBlank()) ? sidHeader : asid;
    var v=sessionValidator.validate(sid); if(!v.active()) return ResponseEntity.status(401).build();
    var req = buildMessagesRequest(v, new MessageFilters(deviceId, recipient, status, type, from, to), page, size);
        var resp = grpc.list(req);
        List<Map<String,Object>> items = resp.getItemsList().stream().map(it -> {
            Map<String,Object> d = new HashMap<>();
            d.put("deviceId", it.getDeviceId());
            d.put("recipient", it.getRecipient());
            d.put("messageContent", it.getMessageContent());
            d.put("messageStatus", it.getMessageStatus().isBlank()? null : it.getMessageStatus());
            d.put("messageType", it.getMessageType().isBlank()? null : it.getMessageType());
            d.put("eventTimestamp", Instant.ofEpochMilli(it.getEventTsMs()));
            return d;
        }).toList();
        Map<String,Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", resp.getPage());
        out.put("size", resp.getSize());
        out.put("count", resp.getCount());
        return ResponseEntity.ok(out);
    }

    private ListMessagesRequest buildMessagesRequest(SessionValidator.Result session,
                                                     MessageFilters filters,
                                                     Integer page,
                                                     Integer size) {
        boolean explicitUnpaged = size != null && size <= 0;
        boolean unpaged = (page == null && size == null) || explicitUnpaged;
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 50 : Math.max(1, Math.min(200, size));
        return ListMessagesRequest.newBuilder()
                .setAdminId(session.adminId()==null?"":session.adminId())
                .setAppId(session.appId()==null?"":session.appId())
                .setDeviceId(filters.deviceId()==null?"":filters.deviceId())
                .setRecipient(filters.recipient()==null?"":filters.recipient())
                .setStatus(filters.status()==null?"":filters.status())
                .setType(filters.type()==null?"":filters.type())
                .setFromMs(parseInstantMs(filters.from()))
                .setToMs(parseInstantMs(filters.to()))
                .setPage(unpaged ? 0 : safePage)
                .setSize(unpaged ? 0 : safeSize)
                .build();
    }

    private record MessageFilters(String deviceId, String recipient, String status, String type, String from, String to) {}

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

