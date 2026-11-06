package app.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProps {
    private String queue;

    public String getQueue() { return queue; }
    public void setQueue(String queue) { this.queue = queue; }
}
