package app.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProps {
    private String queue;
    private String storage;
    public String getQueue() { return queue; }
    public void setQueue(String queue) { this.queue = queue; }
    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }
}
