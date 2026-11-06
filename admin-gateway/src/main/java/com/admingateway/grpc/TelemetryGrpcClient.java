package com.admingateway.grpc;

import com.admingateway.grpc.telemetry.ListTelemetryRequest;
import com.admingateway.grpc.telemetry.ListTelemetryResponse;
import com.admingateway.grpc.telemetry.TelemetryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TelemetryGrpcClient {
    @Value("${gateway.telemetry-grpc.host:localhost}") private String host;
    @Value("${gateway.telemetry-grpc.port:9195}") private int port;

    private ManagedChannel channel;
    private TelemetryServiceGrpc.TelemetryServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = TelemetryServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdownNow();
    }

    public ListTelemetryResponse list(ListTelemetryRequest req) { return stub.listTelemetry(req); }
}
