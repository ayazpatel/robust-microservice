package app.service.store;

import lombok.Data;

@Data
public class VpaQuery {
    private String adminId;
    private String appId;
    private String deviceId; // optional
    private String q;        // search by upiId or phoneNumber
    private int page = 0;
    private int size = 50;
}
