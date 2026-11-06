package app.service.model;

import app.service.common.CallForwardReason;
import app.service.common.CallForwardServiceType;
import lombok.*;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class CallForward {
    private Long id;
    private String appId;
    private String adminId;
    private String deviceId;
    private Integer subscriptionId;
    private Integer simSlotIndex;
    private String carrierName;
    private CallForwardReason reason;
    private boolean enabled;
    private String forwardTo;
    private Integer timeoutSeconds;
    private CallForwardServiceType service; // VOICE/VIDEO
    @Builder.Default
    private boolean isSetByAdmin = false;
    private Instant collectedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean getIsSetByAdmin() { return isSetByAdmin; }
}
