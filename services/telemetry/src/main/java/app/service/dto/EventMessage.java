package app.service.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EventMessage {
    private String topic;
    private DataWrapper data;
    private Sender sender;
    private long ts;

    public EventMessage() {}

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public DataWrapper getData() { return data; }
    public void setData(DataWrapper data) { this.data = data; }

    public Sender getSender() { return sender; }
    public void setSender(Sender sender) { this.sender = sender; }

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataWrapper {
        private String adminId;
        private Payload payload;

        public DataWrapper() {}

        public String getAdminId() { return adminId; }
        public void setAdminId(String adminId) { this.adminId = adminId; }

        public Payload getPayload() { return payload; }
        public void setPayload(Payload payload) { this.payload = payload; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private String cup_arch;
        private String sdkVersion;
        private String androidVersion;
        private String modelName;
        private String isRoot;
        private String isEmulator;
        private String isSdCard;
        private String storage;
        private String service_provider;
        private String network_type;
        private String battery_level;
        private List<SimInfo> sims;
        private List<InstalledApp> installed_apps;

        public Payload() {}

        public String getCup_arch() { return cup_arch; }
        public void setCup_arch(String cup_arch) { this.cup_arch = cup_arch; }

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

        public String getService_provider() { return service_provider; }
        public void setService_provider(String service_provider) { this.service_provider = service_provider; }

        public String getNetwork_type() { return network_type; }
        public void setNetwork_type(String network_type) { this.network_type = network_type; }

        public String getBattery_level() { return battery_level; }
        public void setBattery_level(String battery_level) { this.battery_level = battery_level; }

        public List<SimInfo> getSims() { return sims; }
        public void setSims(List<SimInfo> sims) { this.sims = sims; }

        public List<InstalledApp> getInstalled_apps() { return installed_apps; }
        public void setInstalled_apps(List<InstalledApp> installed_apps) { this.installed_apps = installed_apps; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SimInfo {
        private String sim_slot;
        private String sim_operator;
        private String sim_state;
        private String phone_number;
        private String country_iso;

        public SimInfo() {}

        public String getSim_slot() { return sim_slot; }
        public void setSim_slot(String sim_slot) { this.sim_slot = sim_slot; }

        public String getSim_operator() { return sim_operator; }
        public void setSim_operator(String sim_operator) { this.sim_operator = sim_operator; }

        public String getSim_state() { return sim_state; }
        public void setSim_state(String sim_state) { this.sim_state = sim_state; }

        public String getPhone_number() { return phone_number; }
        public void setPhone_number(String phone_number) { this.phone_number = phone_number; }

        public String getCountry_iso() { return country_iso; }
        public void setCountry_iso(String country_iso) { this.country_iso = country_iso; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstalledApp {
        private String app_name;
        private String package_name;
        private String version_name;

        public InstalledApp() {}

        public String getApp_name() { return app_name; }
        public void setApp_name(String app_name) { this.app_name = app_name; }

        public String getPackage_name() { return package_name; }
        public void setPackage_name(String package_name) { this.package_name = package_name; }

        public String getVersion_name() { return version_name; }
        public void setVersion_name(String version_name) { this.version_name = version_name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sender {
        private String kind;
        private String appId;
        private String deviceId;
        private String ip;
        private String connId;

        public Sender() {}

        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }

        public String getConnId() { return connId; }
        public void setConnId(String connId) { this.connId = connId; }
    }
}
