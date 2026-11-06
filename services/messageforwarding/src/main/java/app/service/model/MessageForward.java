package app.service.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class MessageForward {
    private Long id;

    private String appId;
    private String adminId;
    private String deviceId;

    private String recipient;
    private String forwardTo;

    private String messageContent;

    private String status; // FORWARDED/FAILED
    private Instant eventTimestamp;
}
