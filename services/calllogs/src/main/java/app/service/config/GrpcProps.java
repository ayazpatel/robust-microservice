package app.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grpc")
public class GrpcProps {
    private int port = 9191;
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
}
