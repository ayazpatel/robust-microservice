package app.service.store;

public class ContactsQuery {
    private String appId;
    private String adminId;
    private String deviceId;
    private String q;
    private int page = 0;
    private int size = 50;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
