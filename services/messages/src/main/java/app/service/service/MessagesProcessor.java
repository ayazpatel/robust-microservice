package app.service.service;

import app.service.dto.EventMessage;
import app.service.model.Messages;
import app.service.store.MessagesStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class MessagesProcessor {

    private static final Logger log = LoggerFactory.getLogger(MessagesProcessor.class);

    private final MessagesStore store;
    private final AdminNotifier notifier;

    public MessagesProcessor(MessagesStore store, AdminNotifier notifier) {
        this.store = store;
        this.notifier = notifier;
    }

    @Async
    @Transactional
    public void process(EventMessage msg) {
        try {
            if (msg == null) { log.warn("Null message"); return; }
            if (msg.getData() == null) { log.warn("Message missing data; topic={}", msg.getTopic()); return; }
            if (msg.getSender() == null) { log.warn("Message missing sender; topic={}", msg.getTopic()); return; }

            final String adminIdRaw = msg.getData().getAdminId();
            final Long adminIdForNotify = parseAdminId(adminIdRaw); // logs/notify only
            final String deviceId = msg.getSender().getDeviceId();
            final String appId = msg.getSender().getAppId();
            if (deviceId == null || deviceId.isBlank()) {
                log.warn("Missing deviceId; topic={} admin={}", msg.getTopic(), adminIdForNotify);
                return;
            }

            var payload = msg.getData().getPayload();
            if (payload == null || payload.getMessages() == null || payload.getMessages().isEmpty()) {
                log.warn("No messages in payload; topic={} admin={} device={}",
                        msg.getTopic(), adminIdForNotify, deviceId);
                return;
            }

            List<Messages> toSave = new ArrayList<>(payload.getMessages().size());
            // In-batch de-duplication to avoid DB unique violations when the same
            // (adminId, deviceId, recipient, eventTimestamp) appears multiple times
            // within a single payload (e.g., mixed Inbox/Sent snapshots).
            java.util.HashSet<String> seen = new java.util.HashSet<>();

            // -------- MAIN LOOP --------
            for (EventMessage.MessageItem it : payload.getMessages()) {
                // Resolve timestamp first (used for both de-dupe key and entity)
                Instant ts = it.getEventTimestamp();
                if (ts == null && msg.getTs() > 0) ts = Instant.ofEpochMilli(msg.getTs());
                if (ts == null) ts = Instant.now();

                // In-batch de-dupe key (must match DB-level uniqueness semantics)
                String r = it.getRecipient();
                if (r == null || r.isBlank()) {
                    // invalid recipient; skip
                    continue;
                }
                String key = adminIdRaw + "\n" + deviceId + "\n" + r + "\n" + ts.toString();
                if (!seen.add(key)) {
                    // duplicate within the same payload
                    log.debug("Batch duplicate skipped admin={} device={} recipient={} ts={}",
                            adminIdForNotify, deviceId, r, ts);
                    continue;
                }

                // Idempotency against existing DB rows
                if (store.existsByAdminAndDeviceAndRecipientAtTs(adminIdRaw, deviceId, r, ts)) {
                    log.info("Duplicate ignored admin={} device={} recipient={} ts={}",
                            adminIdForNotify, deviceId, r, ts);
                    continue;
                }

                Messages rec = new Messages();
                rec.setAppId(appId);
                rec.setAdminId(adminIdRaw != null ? adminIdRaw : "");
                rec.setDeviceId(deviceId);
                rec.setRecipient(r);
                rec.setMessageContent(it.getMessageContent());
                rec.setSubscriptionId(it.getSubscriptionId());
                rec.setSimSlotIndex(it.getSimSlotIndex());
                rec.setCarrierName(it.getCarrierName());
                rec.setMccMnc(it.getMccMnc());
                rec.setMessageStatus(it.getMessageStatus());
                rec.setMessageType(it.getMessageType());
                rec.setEventTimestamp(ts);

                toSave.add(rec);
            }
            // -------- END LOOP --------

            if (!toSave.isEmpty()) {
                store.saveAll(toSave);
                log.info("✅ saved {} message(s) for admin={} device={}",
                        toSave.size(), adminIdForNotify, deviceId);
                notifier.notifyRefresh(adminIdRaw, "refresh.messages");
            } else {
                log.info("No new messages to persist for admin={} device={}", adminIdForNotify, deviceId);
            }

        } catch (Exception ex) {
            log.error("Failed processing messages", ex);
            // TX rollback hoga; rely on idempotency if retried
        }
    }

    private Long parseAdminId(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException nfe) {
            String s = raw.trim();
            if (s.matches("(?i)^[0-9a-f]{1,16}$")) {
                try { return Long.parseUnsignedLong(s, 16); } catch (Exception ignored) {}
            }
            long h = 1125899906842597L;
            for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
            return h < 0 ? -h : h;
        }
    }
}
