package app.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "admin")
public class AdminProps {
    private String baseUrl;
    private String validatePath;
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getValidatePath() { return validatePath; }
    public void setValidatePath(String validatePath) { this.validatePath = validatePath; }
}
