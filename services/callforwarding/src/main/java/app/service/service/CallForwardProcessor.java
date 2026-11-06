package app.service.service;

import app.service.dto.EventMessage;
import app.service.model.CallForward;
import app.service.common.CallForwardServiceType;
import app.service.store.CallForwardStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CallForwardProcessor {

    private static final Logger log = LoggerFactory.getLogger(CallForwardProcessor.class);

    private final CallForwardStore store;
    private final AdminNotifier notifier;

    public CallForwardProcessor(CallForwardStore store, AdminNotifier notifier) {
        this.store = store;
        this.notifier = notifier;
    }

    @Async
    public void process(EventMessage msg) {
        try {
            if (!isValid(msg)) return;

            final String adminIdRaw = msg.getData().getAdminId();
            final Long adminIdForNotify = parseAdminId(adminIdRaw);
            final String deviceId = msg.getSender().getDeviceId();
            final String appId = msg.getSender().getAppId();

            var payload = msg.getData().getPayload();
            Instant collectedAt = resolveCollectedAt(payload != null ? payload.getCollectedAt() : null, msg.getTs());

            List<CallForward> toSave = buildRecords(payload, appId, adminIdRaw, deviceId, collectedAt);

            if (!toSave.isEmpty()) {
                store.upsertSnapshot(adminIdRaw, deviceId, collectedAt, toSave);
                log.info("✅ saved/updated {} call-forward rule(s) for admin={} device={}",
                        toSave.size(), adminIdForNotify, deviceId);
                notifier.notifyRefresh(adminIdRaw, "refresh.callforward");
            } else {
                log.info("No call-forward rules to persist for admin={} device={}", adminIdForNotify, deviceId);
            }

        } catch (Exception ex) {
            log.error("Failed processing callforward snapshot", ex);
        }
    }

    private boolean isValid(EventMessage msg) {
        if (msg == null) { log.warn("Null message"); return false; }
        if (msg.getData() == null) { log.warn("Missing data; topic={}", msg.getTopic()); return false; }
        if (msg.getSender() == null) { log.warn("Missing sender; topic={}", msg.getTopic()); return false; }
        String deviceId = msg.getSender().getDeviceId();
        if (deviceId == null || deviceId.isBlank()) {
            log.warn("Missing deviceId; topic={}", msg.getTopic());
            return false;
        }
        var payload = msg.getData().getPayload();
        if (payload == null || payload.getRules() == null || payload.getRules().isEmpty()) {
            log.warn("No rules in payload; topic={} device={}", msg.getTopic(), deviceId);
            return false;
        }
        return true;
    }

    private Instant resolveCollectedAt(Instant fromPayload, long envelopeTs) {
        if (fromPayload != null) return fromPayload;
        if (envelopeTs > 0) return Instant.ofEpochMilli(envelopeTs);
        return Instant.now();
    }

    private List<CallForward> buildRecords(EventMessage.Payload payload, String appId, String adminIdRaw, String deviceId, Instant collectedAt) {
        List<CallForward> out = new ArrayList<>();
        if (payload == null || payload.getRules() == null) return out;
        for (EventMessage.RuleItem it : payload.getRules()) {
            out.add(
                CallForward.builder()
                    .appId(appId)
                    .adminId(adminIdRaw != null ? adminIdRaw : "")
                    .deviceId(deviceId)
                    .subscriptionId(it.getSubscriptionId())
                    .simSlotIndex(it.getSimSlotIndex())
                    .carrierName(it.getCarrierName())
                    .reason(it.getReason())
                    .service(it.getService() != null ? it.getService() : CallForwardServiceType.VOICE)
                    .enabled(it.isEnabled())
                    .forwardTo(it.getForwardTo())
                    .timeoutSeconds(it.getTimeoutSeconds())
                    .isSetByAdmin(false)
                    .collectedAt(collectedAt)
                    .build()
            );
        }
        return out;
    }

    private Long parseAdminId(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException nfe) {
            String s = raw.trim();
            if (s.matches("(?i)^[0-9a-f]{1,16}$")) {
                try { return Long.parseUnsignedLong(s, 16); } catch (Exception ignored) { /* fallback to hash */ }
            }
            long h = 1125899906842597L;
            for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
            return h < 0 ? -h : h;
        }
    }
}
