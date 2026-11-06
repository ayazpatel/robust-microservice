package app.service.dto;

import app.service.common.MessageStatus;
import app.service.common.MessageType;
import com.fasterxml.jackson.annotation.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventMessage {

    private String topic;        // e.g. "service.messages.batch"
    private DataWrapper data;    // contains adminId + payload
    private Sender sender;       // optional, if producer adds it
    private long ts;             // optional epoch millis

    // --- data wrapper ---
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataWrapper {
        private String adminId;
        private Payload payload;
    }

    // --- payload with only messages[] ---
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private List<MessageItem> messages;
    }

    // --- single message item (matches your Messages entity fields) ---
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageItem {
        private String recipient;
        private String messageContent;
        private Integer subscriptionId;   // Android subscription id (nullable)
        private Integer simSlotIndex;     // 0/1.. (nullable)
        private String carrierName;       // e.g., "Airtel"
        private String mccMnc;            // e.g., "40445"

        @JsonProperty("messageStatus")
        private MessageStatus messageStatus; // SENT, FAILED, PENDING

        @JsonProperty("messageType")
        private MessageType messageType;     // RECEIVED, SENT

        /** ISO-8601 timestamp preferred (e.g. 2025-10-18T07:21:13Z) */
        private Instant eventTimestamp;
    }

    // --- optional sender block (unchanged) ---
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sender {
        private String kind;
        private String appId;
        private String deviceId;
        private String ip;
        private String connId;
    }
}
