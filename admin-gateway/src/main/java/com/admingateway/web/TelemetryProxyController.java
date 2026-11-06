package com.admingateway.web;

import com.admingateway.grpc.TelemetryGrpcClient;
import com.admingateway.grpc.telemetry.ListTelemetryRequest;
import com.admingateway.security.SessionValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/telemetry")
public class TelemetryProxyController {
    private final SessionValidator sessionValidator;
    private final TelemetryGrpcClient grpc;
    public TelemetryProxyController(SessionValidator sessionValidator, TelemetryGrpcClient grpc) { this.sessionValidator = sessionValidator; this.grpc = grpc; }

    @GetMapping("/records")
    public ResponseEntity<Map<String,Object>> records(@CookieValue(value = "asid", required = false) String asid,
                                     @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
                                     @RequestParam(value = "deviceId", required = false) String deviceId,
                                     @RequestParam(value = "from", required = false) String from,
                                     @RequestParam(value = "to", required = false) String to,
                                     @RequestParam(value = "page", required = false) Integer page,
                                     @RequestParam(value = "size", required = false) Integer size) {
        String sid = (sidHeader != null && !sidHeader.isBlank()) ? sidHeader : asid;
        var res = sessionValidator.validate(sid);
        if (!res.active()) return ResponseEntity.status(401).build();
    // If admin has no assigned app, fail fast with a clear message rather than querying an empty app partition
    if (res.appId()==null || res.appId().isBlank()) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "missing_app_id",
            "message", "Admin has no assigned app. Set Assigned App for this admin in Admin panel and retry."
        ));
    }
    boolean explicitUnpaged = size != null && size <= 0;
    boolean unpaged = (page == null && size == null) || explicitUnpaged;
    int safePage = page == null ? 0 : Math.max(0, page);
    int safeSize = size == null ? 50 : Math.max(1, Math.min(200, size));

    var req = ListTelemetryRequest.newBuilder()
        .setAdminId(res.adminId()==null?"":res.adminId())
        .setAppId(res.appId()==null?"":res.appId())
                .setDeviceId(deviceId==null?"":deviceId)
                .setFromMs(parseMs(from))
                .setToMs(parseMs(to))
        .setPage(unpaged ? 0 : safePage)
        .setSize(unpaged ? 0 : safeSize)
                .build();
        var resp = grpc.list(req);
        List<Map<String,Object>> items = resp.getItemsList().stream().map(it -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", it.getDeviceId());
            m.put("deviceId", it.getDeviceId());
            m.put("eventTs", Instant.ofEpochMilli(it.getEventTsMs()));
            m.put("androidVersion", it.getAndroidVersion());
            m.put("modelName", it.getModelName());
            m.put("batteryLevel", it.getBatteryLevel());
            m.put("cpuArch", it.getCpuArch());
            m.put("sdkVersion", it.getSdkVersion());
            m.put("isRoot", it.getIsRoot());
            m.put("isEmulator", it.getIsEmulator());
            m.put("isSdCard", it.getIsSdCard());
            m.put("storage", it.getStorage());
            m.put("serviceProvider", it.getServiceProvider());
            m.put("networkType", it.getNetworkType());
            return m;
        }).toList();
        Map<String,Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", resp.getPage());
        out.put("size", resp.getSize());
        out.put("count", resp.getCount());
        return ResponseEntity.ok(out);
    }

    private static long parseMs(String s){
        if(s==null||s.isBlank()) return 0L;
        try{
            String t=s.trim();
            if(t.matches("^\\d{10}$")) {
                return Long.parseLong(t)*1000L;
            } else if(t.matches("^\\d{13}$")) {
                return Long.parseLong(t);
            } else {
                return Instant.parse(t).toEpochMilli();
            }
        } catch(Exception e){
            return 0L;
        }
    }
}
