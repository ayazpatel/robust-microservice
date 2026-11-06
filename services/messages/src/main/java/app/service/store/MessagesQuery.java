package app.service.store;

import app.service.common.MessageStatus;
import app.service.common.MessageType;

import java.time.Instant;

public record MessagesQuery(
        String appId,
        String adminId,
        String deviceId,
        String recipient,
        MessageStatus status,
        MessageType type,
        Instant from,
        Instant to,
        int page,
        int size
) {}
