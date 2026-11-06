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

    @Value("${grpc.port:9199}")
    private int port;

    private Server server;

    private final CardsGrpcService cardsService;

    public GrpcServerConfig(CardsGrpcService cardsService) {
        this.cardsService = cardsService;
    }

    @PostConstruct
    public void start() throws IOException {
        server = NettyServerBuilder
                .forAddress(new InetSocketAddress("0.0.0.0", port))
                .addService(cardsService)
                .build()
                .start();
    }

    @PreDestroy
    public void stop() {
        if (server != null) server.shutdown();
    }
}
