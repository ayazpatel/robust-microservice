package app.service.store;

import app.service.model.Messages;

import java.time.Instant;
import java.util.List;

/**
 * Redis-backed store abstraction for Messages, used to replace JPA.
 */
public interface MessagesStore {
    boolean existsByAdminAndDeviceAndRecipientAtTs(String adminId, String deviceId, String recipient, Instant ts);

    void saveAll(List<Messages> items);

    List<Messages> list(MessagesQuery q);
}
