package app.service.store;

import app.service.model.ContactMin;

import java.time.Instant;
import java.util.List;

public interface ContactsStore {
    void upsertSnapshot(String adminId, String deviceId, Instant collectedAt, List<ContactMin> contacts);
    List<ContactMin> list(ContactsQuery q);
}
