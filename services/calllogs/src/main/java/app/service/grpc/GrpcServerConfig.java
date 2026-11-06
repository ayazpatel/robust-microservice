package app.service.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcServerConfig {
    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);

    private final CallLogsGrpcService callLogsGrpcService;
    private Server server;

    public GrpcServerConfig(CallLogsGrpcService callLogsGrpcService) {
        this.callLogsGrpcService = callLogsGrpcService;
    }

    @Value("${grpc.port:9191}")
    private int port;

    @PostConstruct
    public void start() throws IOException {
        server = NettyServerBuilder.forPort(port)
                .addService(callLogsGrpcService)
                .build()
                .start();
        log.info("gRPC server started on port {}", port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stop();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // preserve interrupt status
            }
        }));
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(10, TimeUnit.SECONDS);
            log.info("gRPC server stopped");
        }
    }
}
