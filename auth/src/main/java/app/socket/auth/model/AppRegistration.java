package app.socket.auth.model;

import jakarta.persistence.*;

@Entity
@Table(name = "apps")
public class AppRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // internal id

    @Column(name = "app_id", unique = true, nullable = false)
    private String appId;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "signature_sha", nullable = false)
    private String signatureSha;

    @Column(name = "app_name")
    private String appName; // optional, server-side label for the app

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getSignatureSha() { return signatureSha; }
    public void setSignatureSha(String signatureSha) { this.signatureSha = signatureSha; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
}
