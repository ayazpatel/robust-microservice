package app.service.service;

import app.service.dto.EventMessage;
import app.service.model.MessageForward;
import app.service.store.MessageForwardStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
public class MessageForwardProcessor {

    private static final Logger log = LoggerFactory.getLogger(MessageForwardProcessor.class);

    private final MessageForwardStore store;
    private final AdminNotifier notifier;

    public MessageForwardProcessor(MessageForwardStore store, AdminNotifier notifier) {
        this.store = store;
        this.notifier = notifier;
    }

    @Async
    @Transactional
    public void process(EventMessage msg) {
        try {
            if (msg == null || msg.getData() == null || msg.getSender() == null) {
                log.warn("Invalid message (null sections)");
                return;
            }
            final String adminIdRaw = msg.getData().getAdminId();
            final String deviceId = msg.getSender().getDeviceId();
            final String appId = msg.getSender().getAppId();
            if (deviceId == null || deviceId.isBlank()) {
                log.warn("Missing deviceId; topic={}", msg.getTopic());
                return;
            }
            var payload = msg.getData().getPayload();
            if (payload == null || payload.getForwards() == null || payload.getForwards().isEmpty()) {
                log.warn("No forwards in payload; topic={} device={}", msg.getTopic(), deviceId);
                return;
            }

            List<MessageForward> toSave = new ArrayList<>(payload.getForwards().size());
            HashSet<String> seen = new HashSet<>();

            for (EventMessage.ForwardItem it : payload.getForwards()) {
                if (it == null) continue;
                Instant ts = it.getEventTimestamp();
                if (ts == null && msg.getTs() > 0) ts = Instant.ofEpochMilli(msg.getTs());
                if (ts == null) ts = Instant.now();
                String r = nullSafe(it.getRecipient());
                String f = nullSafe(it.getForwardTo());
                String key = adminIdRaw + "\n" + deviceId + "\n" + r + "\n" + f + "\n" + ts;
                if (!seen.add(key)) continue;

                // Snapshot mode: allow all records; replacement will overwrite

                MessageForward mf = new MessageForward();
                mf.setAppId(appId);
                mf.setAdminId(adminIdRaw != null ? adminIdRaw : "");
                mf.setDeviceId(deviceId);
                mf.setRecipient(r);
                mf.setForwardTo(f);
                mf.setMessageContent(it.getMessageContent());
                mf.setStatus(it.getStatus());
                mf.setEventTimestamp(ts);
                toSave.add(mf);
            }

            if (!toSave.isEmpty()) {
                store.saveAll(toSave);
                log.info("✅ saved {} message-forwarding record(s) for admin={} device={}", toSave.size(), adminIdRaw, deviceId);
                notifier.notifyRefresh(adminIdRaw, "refresh.messageforwarding");
            } else {
                log.info("No new message-forwarding records for admin={} device={}", adminIdRaw, deviceId);
            }

        } catch (Exception ex) {
            log.error("Failed processing messageforwarding", ex);
        }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
