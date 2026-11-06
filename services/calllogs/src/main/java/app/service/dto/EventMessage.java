package app.service.dto;

import app.service.common.CallType;
import com.fasterxml.jackson.annotation.*;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventMessage {
    private String topic;                // "service.calllog.batch"
    private DataWrapper data;
    private Sender sender;               // { deviceId: ... }
    private long ts;                     // envelope ts (optional)

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataWrapper {
        private String adminId;
        private Payload payload;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        @JsonAlias({"calls","callLogs"})
        private List<CallItem> calls;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallItem {
        private Long platformId;
        private String number;

        /** Will parse JSON strings like "OUTGOING", "MISSED", etc. */
        private CallType type;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant eventTimestamp;

        private Integer durationSec;
        private String cachedName;
        private String cachedLookupUri;
        private String phoneAccountId;
        private Integer subscriptionId;
        private Integer simSlotIndex;
        private String carrierName;
        private String mccMnc;
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
