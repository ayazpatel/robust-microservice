package app.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public class AdminProps {
    private String baseUrl;
    private String validatePath;
    private String refreshUrl;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getValidatePath() { return validatePath; }
    public void setValidatePath(String validatePath) { this.validatePath = validatePath; }

    public String getRefreshUrl() { return refreshUrl; }
    public void setRefreshUrl(String refreshUrl) { this.refreshUrl = refreshUrl; }
}
