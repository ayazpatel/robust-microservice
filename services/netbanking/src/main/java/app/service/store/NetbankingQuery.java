package app.service.store;

import lombok.Data;

@Data
public class NetbankingQuery {
    private String adminId;
    private String appId;
    private String deviceId; // optional
    private String q;        // search by bankName/accountNumber/phoneNumber
    private int page = 0;
    private int size = 50;
}
