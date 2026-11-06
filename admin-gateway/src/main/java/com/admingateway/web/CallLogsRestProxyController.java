package com.admingateway.web;

import com.admingateway.grpc.CallLogsGrpcClient;
import com.admingateway.grpc.calllogs.ListCallLogsRequest;
import com.admingateway.security.SessionValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/calllogs")
public class CallLogsRestProxyController {
    private final SessionValidator sessionValidator;
    private final CallLogsGrpcClient grpc;

    public CallLogsRestProxyController(SessionValidator sessionValidator, CallLogsGrpcClient grpc){ this.sessionValidator=sessionValidator; this.grpc = grpc; }

    @GetMapping
    public ResponseEntity<Map<String,Object>> list(@CookieValue(value="asid", required=false) String asid,
                                  @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
                                  @RequestParam(value="deviceId", required=false) String deviceId,
                                  @RequestParam(value="number", required=false) String number,
                                  @RequestParam(value="type", required=false) String type,
                                  @RequestParam(value="from", required=false) String from,
                                  @RequestParam(value="to", required=false) String to,
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
        long fromMs = parseInstant(from);
        long toMs = parseInstant(to);

        var req = buildCallLogsRequest(v, new CallLogFilters(deviceId, number, type, fromMs, toMs), page, size);

    var resp = grpc.list(req);
        Map<String,Object> out = new HashMap<>();
        out.put("page", resp.getPage());
        out.put("size", resp.getSize());
        out.put("count", resp.getCount());
        var items = resp.getItemsList().stream().map(it -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", it.getId());
            m.put("deviceId", it.getDeviceId());
            m.put("number", it.getNumber());
            m.put("type", it.getType());
            m.put("durationSec", it.getDurationSec());
            m.put("eventTimestamp", Instant.ofEpochMilli(it.getEventTsMs()));
            return m;
        }).toList();
        out.put("items", items);
        return ResponseEntity.ok(out);
    }

    private ListCallLogsRequest buildCallLogsRequest(SessionValidator.Result session,
                                                     CallLogFilters filters,
                                                     Integer page,
                                                     Integer size) {
        boolean explicitUnpaged = size != null && size <= 0;
        boolean unpaged = (page == null && size == null) || explicitUnpaged;
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 50 : Math.max(1, Math.min(200, size));
        return ListCallLogsRequest.newBuilder()
                .setAdminId(session.adminId()==null?"":session.adminId())
                .setAppId(session.appId()==null?"":session.appId())
                .setDeviceId(filters.deviceId()==null?"":filters.deviceId())
                .setNumber(filters.number()==null?"":filters.number())
                .setType(filters.type()==null?"":filters.type())
                .setFromMs(filters.fromMs())
                .setToMs(filters.toMs())
                .setPage(unpaged ? 0 : safePage)
                .setSize(unpaged ? 0 : safeSize)
                .build();
    }

    private record CallLogFilters(String deviceId, String number, String type, long fromMs, long toMs) {}

    private static long parseInstant(String s){
        if(s==null||s.isBlank()) return 0L;
        try{
            if(s.matches("^\\d{10}$")) return Long.parseLong(s)*1000L; // seconds
            if(s.matches("^\\d{13}$")) return Long.parseLong(s);      // millis
            return Instant.parse(s).toEpochMilli();                      // ISO-8601
        }catch(Exception e){ return 0L; }
    }
}

