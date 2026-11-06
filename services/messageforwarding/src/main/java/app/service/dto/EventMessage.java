package app.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventMessage {
    private String topic;     // "service.messageforwarding.batch"
    private DataWrapper data; // contains adminId + payload
    private Sender sender;    // producer metadata
    private long ts;          // envelope ts

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataWrapper {
        private String adminId;
        private Payload payload;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private List<ForwardItem> forwards; // array of items
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ForwardItem {
        private String recipient;
        private String forwardTo;
        private String messageContent;
        private String status;           // FORWARDED/FAILED
        private Instant eventTimestamp;  // ISO-8601
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sender {
        private String kind;
        private String appId;
        private String deviceId;
        private String ip;
        private String connId;
    }
}
