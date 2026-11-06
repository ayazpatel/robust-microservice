package app.service.store;

import java.time.Instant;

public class TelemetryQuery {
    private String appId;
    private String adminId;
    private String deviceId;
    private Instant from;
    private Instant to;
    private int page = 0;
    private int size = 50;
    private boolean unpaged;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Instant getFrom() { return from; }
    public void setFrom(Instant from) { this.from = from; }
    public Instant getTo() { return to; }
    public void setTo(Instant to) { this.to = to; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public boolean isUnpaged() { return unpaged; }
    public void setUnpaged(boolean unpaged) { this.unpaged = unpaged; }
}
