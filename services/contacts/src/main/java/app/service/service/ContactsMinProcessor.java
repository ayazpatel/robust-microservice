package app.service.service;

import app.service.dto.EventMessage;
import app.service.model.ContactMin;
import app.service.store.ContactsStore;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class ContactsMinProcessor {

    private static final Logger log = LoggerFactory.getLogger(ContactsMinProcessor.class);

    private final ContactsStore store;
    private final AdminNotifier notifier;

    public ContactsMinProcessor(ContactsStore store, AdminNotifier notifier) {
        this.store = store;
        this.notifier = notifier;
    }

    @Async
    public void process(EventMessage msg) {
        if (!validEnvelope(msg)) return;
        final String adminId = msg.getData().getAdminId();
        final String deviceId = msg.getSender().getDeviceId();
        final String appId = (msg.getSender().getAppId()==null || msg.getSender().getAppId().isBlank()) ? "unknown" : msg.getSender().getAppId();
        var payload = msg.getData().getPayload();
        if (payload == null || payload.getContacts() == null || payload.getContacts().isEmpty()) { log.info("contacts snapshot: empty"); return; }

        Instant collectedAt = resolveCollectedAt(payload.getCollectedAt(), msg.getTs());
        List<ContactMin> contacts = mapContacts(appId, adminId, deviceId, collectedAt, payload.getContacts());
        store.upsertSnapshot(adminId, deviceId, collectedAt, contacts);
        log.info("✅ contacts_min upserted={} appId={} admin={} device={}", contacts.size(), appId, adminId, deviceId);
        notifier.notifyRefresh(adminId, "refresh.contacts.min");
    }

    private boolean validEnvelope(EventMessage msg){
        if (msg == null || msg.getData() == null || msg.getSender() == null) {
            log.warn("contacts snapshot: bad envelope");
            return false;
        }
        return true;
    }

    private Instant resolveCollectedAt(Instant collectedAt, long ts){
        if (collectedAt == null && ts > 0) collectedAt = Instant.ofEpochMilli(ts);
        if (collectedAt == null) collectedAt = Instant.now();
        return collectedAt;
    }

    private List<ContactMin> mapContacts(String appId, String adminId, String deviceId, Instant collectedAt, java.util.List<app.service.dto.EventMessage.ContactItem> items){
        List<ContactMin> contacts = new ArrayList<>(items.size());
        for (var it : items) {
            if (it.getLookupKey() == null || it.getLookupKey().isBlank()) continue;
            ContactMin c = ContactMin.builder()
                    .appId(appId)
                    .adminId(adminId)
                    .deviceId(deviceId)
                    .lookupKey(it.getLookupKey())
                    .displayName(it.getDisplayName())
                    .collectedAt(collectedAt)
                    .createdAt(collectedAt)
                    .updatedAt(collectedAt)
                    .build();
            if (it.getNumbers() != null) {
                for (String num : it.getNumbers()) {
                    if (num == null || num.isBlank()) continue;
                    c.getNumbers().add(num.trim());
                }
            }
            contacts.add(c);
        }
        return contacts;
    }
}
