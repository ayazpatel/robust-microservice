package app.service.model;

import app.service.common.MessageStatus;
import app.service.common.MessageType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Messages {
    private String appId;
    private String adminId;
    private String deviceId;
    @NotBlank
    @Size(max = 64)
    @Pattern(
            regexp = "(^\\+?[1-9]\\d{1,14}$)|(^[A-Z]{2}-[A-Z0-9]{3,11}$)|(^\\d{3,8}$)",
            message = "recipient must be E.164 / DLT header / short code"
    )
    private String recipient;
    @NotBlank
    private String messageContent;
    @PositiveOrZero
    private Integer subscriptionId;
    @Min(0)
    private Integer simSlotIndex;
    private String carrierName;
    private String mccMnc;
    @NotNull
    private MessageStatus messageStatus;
    @NotNull
    private MessageType messageType;
    @NotNull
    private Instant eventTimestamp;
}
