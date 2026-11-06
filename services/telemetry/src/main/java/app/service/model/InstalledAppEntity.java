package app.service.model;

public class InstalledAppEntity {

    private String appName;
    private String packageName;
    private String versionName;

    public InstalledAppEntity() {
        // no-op
    }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
}
