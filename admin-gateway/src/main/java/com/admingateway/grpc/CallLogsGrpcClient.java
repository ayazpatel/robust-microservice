package com.admingateway.grpc;

import com.admingateway.grpc.calllogs.CallLogsServiceGrpc;
import com.admingateway.grpc.calllogs.ListCallLogsRequest;
import com.admingateway.grpc.calllogs.ListCallLogsResponse;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CallLogsGrpcClient {
    @Value("${gateway.calllogs-grpc.host:localhost}") private String host;
    @Value("${gateway.calllogs-grpc.port:9191}") private int port;

    private ManagedChannel channel;
    private CallLogsServiceGrpc.CallLogsServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = CallLogsServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdownNow();
    }

    public ListCallLogsResponse list(ListCallLogsRequest req) { return stub.listCallLogs(req); }
}
