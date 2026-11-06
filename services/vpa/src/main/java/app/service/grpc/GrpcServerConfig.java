package app.service.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;

@Component
public class GrpcServerConfig {

    @Value("${grpc.port:9197}")
    private int port;

    private Server server;

    private final VpaGrpcService vpaService;

    public GrpcServerConfig(VpaGrpcService vpaService) { this.vpaService = vpaService; }

    @PostConstruct
    public void start() throws IOException {
        server = NettyServerBuilder
                .forAddress(new InetSocketAddress("0.0.0.0", port))
                .addService(vpaService)
                .build()
                .start();
    }

    @PreDestroy
    public void stop() {
        if (server != null) server.shutdown();
    }
}
