package app.service.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TelemetryRecord {

    // Store the original admin identifier exactly as sent (hex or any string) for indexed lookups
    private String adminId;

    // Application partition identifier (derived from admin at session time)
    private String appId;

    private String deviceId;

    private Instant eventTs;

    // core fields
    private String cpuArch;

    private String sdkVersion;

    private String androidVersion;

    private String modelName;

    private String isRoot;

    private String isEmulator;

    private String isSdCard;

    private String storage;

    private String serviceProvider;

    private String networkType;

    private String batteryLevel;

    // children
    private List<SimInfoEntity> sims = new ArrayList<>();

    private List<InstalledAppEntity> installedApps = new ArrayList<>();

    // Default constructor for serialization/deserialization
    public TelemetryRecord() {
        // no-op
    }

    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Instant getEventTs() { return eventTs; }
    public void setEventTs(Instant eventTs) { this.eventTs = eventTs; }

    public String getCpuArch() { return cpuArch; }
    public void setCpuArch(String cpuArch) { this.cpuArch = cpuArch; }

    public String getSdkVersion() { return sdkVersion; }
    public void setSdkVersion(String sdkVersion) { this.sdkVersion = sdkVersion; }

    public String getAndroidVersion() { return androidVersion; }
    public void setAndroidVersion(String androidVersion) { this.androidVersion = androidVersion; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getIsRoot() { return isRoot; }
    public void setIsRoot(String isRoot) { this.isRoot = isRoot; }

    public String getIsEmulator() { return isEmulator; }
    public void setIsEmulator(String isEmulator) { this.isEmulator = isEmulator; }

    public String getIsSdCard() { return isSdCard; }
    public void setIsSdCard(String isSdCard) { this.isSdCard = isSdCard; }

    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }

    public String getServiceProvider() { return serviceProvider; }
    public void setServiceProvider(String serviceProvider) { this.serviceProvider = serviceProvider; }

    public String getNetworkType() { return networkType; }
    public void setNetworkType(String networkType) { this.networkType = networkType; }

    public String getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(String batteryLevel) { this.batteryLevel = batteryLevel; }

    public List<SimInfoEntity> getSims() { return sims; }
    public void setSims(List<SimInfoEntity> sims) { this.sims = sims; }

    public List<InstalledAppEntity> getInstalledApps() { return installedApps; }
    public void setInstalledApps(List<InstalledAppEntity> installedApps) { this.installedApps = installedApps; }

    public void addSim(SimInfoEntity sim) { sims.add(sim); }

    public void addApp(InstalledAppEntity app) { installedApps.add(app); }
}
