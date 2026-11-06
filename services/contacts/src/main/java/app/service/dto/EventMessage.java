package app.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventMessage {
    private String topic;
    private DataWrapper data;
    private Sender sender;
    private long ts;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataWrapper {
        private String adminId;
        private Payload payload;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private List<ContactItem> contacts;
        private Instant collectedAt; // optional; fallback to msg.ts if null
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContactItem {
        private String lookupKey;
        private String displayName;
        private List<String> numbers; // E.164 preferred; can be raw too
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sender {
        private String kind;
        private String appId;
        private String deviceId;
        private String ip;
        private String connId;
    }
}
