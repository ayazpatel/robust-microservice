// src/main/java/app/service/model/CallLog.java
package app.service.model;

import lombok.*;
import app.service.common.CallType;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CallLog {
    private Long id;
    private String appId;
    private String adminId;
    private String deviceId;
    private Long platformId; // Android row _ID (nullable)
    private String number;
    private CallType type; // INCOMING/OUTGOING/...
    private Instant eventTimestamp;
    private Integer durationSec;
    private String cachedName;
    private String cachedLookupUri;
    private String phoneAccountId;
    private Integer subscriptionId;
    private Integer simSlotIndex;
    private String carrierName;
    private String mccMnc;
    private Instant createdAt;
    private Instant updatedAt;
}
