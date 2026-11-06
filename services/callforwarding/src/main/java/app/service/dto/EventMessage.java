package app.service.dto;

import app.service.common.CallForwardReason;       // UNCONDITIONAL/BUSY/NO_REPLY/NOT_REACHABLE
import app.service.common.CallForwardServiceType;  // VOICE/VIDEO
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventMessage {

    /** e.g. "service.callforward.snapshot" */
    private String topic;

    /** contains adminId + payload */
    private DataWrapper data;

    /** optional metadata from producer */
    private Sender sender;

    /** optional envelope epoch millis */
    private long ts;

    // -------------------- inner types --------------------

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataWrapper {
        private String adminId;
        private Payload payload;
    }

    /** call-forwarding payload */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        /** list of per-SIM rules */
        private List<RuleItem> rules;

        /** when the device collected this snapshot (ISO-8601) */
        private Instant collectedAt;
    }

    /** one call-forwarding rule row (per SIM × reason) */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuleItem {

        private Integer subscriptionId;     // which SIM
        private Integer simSlotIndex;       // 0/1/…
        private String  carrierName;        // Airtel/Jio/etc.

        @JsonProperty("reason")
        private CallForwardReason reason;   // UNCONDITIONAL/BUSY/NO_REPLY/NOT_REACHABLE

        private boolean enabled;            // true/false
        private String  forwardTo;          // E.164 or null
        private int     timeoutSeconds;     // only for NO_REPLY; else 0

        @JsonProperty("service")
        private CallForwardServiceType service; // VOICE/VIDEO

        /** NEW: who set it — snapshots from device should send false */
        private boolean isSetByAdmin;       // default false on snapshots
    }

    /** same sender block you already use */
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
