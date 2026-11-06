package com.admingateway.grpc;

import com.admingateway.grpc.callforwarding.CallForwardingServiceGrpc;
import com.admingateway.grpc.callforwarding.ListCallForwardRequest;
import com.admingateway.grpc.callforwarding.ListCallForwardResponse;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CallForwardingGrpcClient {
    @Value("${gateway.callforwarding-grpc.host:localhost}") private String host;
    @Value("${gateway.callforwarding-grpc.port:9193}") private int port;

    private ManagedChannel channel;
    private CallForwardingServiceGrpc.CallForwardingServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = CallForwardingServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdownNow();
    }

    public ListCallForwardResponse list(ListCallForwardRequest req) {
        return stub.listCallForwardRules(req);
    }
}
