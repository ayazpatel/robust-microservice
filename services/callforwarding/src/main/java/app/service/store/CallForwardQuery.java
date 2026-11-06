package app.service.store;

import app.service.common.CallForwardReason;
import app.service.common.CallForwardServiceType;

public class CallForwardQuery {
    private String appId;
    private String adminId;
    private String deviceId;
    private Integer subscriptionId;
    private CallForwardReason reason;
    private CallForwardServiceType service;
    private int page = 0;
    private int size = 50;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Integer getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Integer subscriptionId) { this.subscriptionId = subscriptionId; }
    public CallForwardReason getReason() { return reason; }
    public void setReason(CallForwardReason reason) { this.reason = reason; }
    public CallForwardServiceType getService() { return service; }
    public void setService(CallForwardServiceType service) { this.service = service; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
