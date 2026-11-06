package app.service.store;

import app.service.common.CallType;
import java.time.Instant;

public class CallLogQuery {
    private String appId;
    private String adminId;
    private String deviceId;
    private String number;
    private CallType type;
    private Instant from;
    private Instant to;
    private int page = 0;
    private int size = 50;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public CallType getType() { return type; }
    public void setType(CallType type) { this.type = type; }
    public Instant getFrom() { return from; }
    public void setFrom(Instant from) { this.from = from; }
    public Instant getTo() { return to; }
    public void setTo(Instant to) { this.to = to; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
