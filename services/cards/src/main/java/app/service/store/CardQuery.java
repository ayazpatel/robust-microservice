package app.service.store;

import lombok.Data;

@Data
public class CardQuery {
    private String adminId;
    private String appId;
    private String deviceId; // optional filter
    private String q;        // search by bin or last4
    private String network;  // optional
    private int page = 0;
    private int size = 50;
}
