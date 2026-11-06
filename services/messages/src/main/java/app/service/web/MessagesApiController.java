package app.service.web;

import app.service.common.MessageStatus;
import app.service.common.MessageType;
import app.service.model.Messages;
import app.service.store.MessagesQuery;
import app.service.store.MessagesStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessagesApiController {
    private final MessagesStore store;
    private final SessionValidator sessionValidator;
    public MessagesApiController(MessagesStore store, SessionValidator sessionValidator){
        this.store = store; this.sessionValidator = sessionValidator;
    }

    @GetMapping
    public ResponseEntity<Map<String,Object>> list(@CookieValue(value="asid", required=false) String asid,
                                  @RequestHeader(value="X-Session-Id", required=false) String sidHeader,
                                  @RequestParam(value="deviceId", required=false) String deviceId,
                                  @RequestParam(value="recipient", required=false) String recipient,
                                  @RequestParam(value="status", required=false) String status,
                                  @RequestParam(value="type", required=false) String type,
                                  @RequestParam(value="from", required=false) String from,
                                  @RequestParam(value="to", required=false) String to,
                                  @RequestParam(value="page", defaultValue="0") int page,
                                  @RequestParam(value="size", defaultValue="50") int size){
        String sid = sidHeader!=null && !sidHeader.isBlank()? sidHeader : asid;
        var v=sessionValidator.validate(sid); if(!v.active()) return ResponseEntity.status(401).build();
        int ps=Math.max(1, Math.min(size,200));
    MessageStatus st = parseStatus(status);
    MessageType tp = parseType(type);
    List<Messages> items = store.list(new MessagesQuery(
        v.appId(), v.adminId(), deviceId, recipient, st, tp, parseInstant(from), parseInstant(to), Math.max(0,page), ps
    ));
        Map<String,Object> out = new HashMap<>();
        out.put("items", items.stream().map(MessagesApiController::toDto).toList());
        out.put("page", page); out.put("size", ps); out.put("count", items.size());
    return ResponseEntity.ok(out);
    }

    private static Map<String,Object> toDto(Messages m){
        Map<String,Object> d=new HashMap<>();
        d.put("deviceId", m.getDeviceId()); d.put("recipient", m.getRecipient());
        d.put("messageContent", m.getMessageContent());
        d.put("messageStatus", m.getMessageStatus()==null?null:m.getMessageStatus().name());
        d.put("messageType", m.getMessageType()==null?null:m.getMessageType().name());
        d.put("eventTimestamp", m.getEventTimestamp());
        return d;
    }
    private static MessageStatus parseStatus(String s){
        if (s==null || s.isBlank()) return null;
        try { return MessageStatus.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { return null; }
    }
    private static MessageType parseType(String s){
        if (s==null || s.isBlank()) return null;
        try { return MessageType.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { return null; }
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s.trim()); } catch (Exception ignored) { /* try next */ }
        try { return Instant.ofEpochMilli(Long.parseLong(s.trim())); } catch (Exception ignored) { /* try seconds */ }
        try { return Instant.ofEpochSecond(Long.parseLong(s.trim())); } catch (Exception ignored) { /* give up */ }
        return null;
    }
}
