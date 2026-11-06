package com.admingateway.grpc;

import com.admingateway.grpc.netbanking.ListNetbankingRequest;
import com.admingateway.grpc.netbanking.ListNetbankingResponse;
import com.admingateway.grpc.netbanking.NetbankingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NetbankingGrpcClient {

    @Value("${gateway.netbanking-grpc.host:localhost}") private String host;
    @Value("${gateway.netbanking-grpc.port:9198}") private int port;

    private ManagedChannel channel;
    private NetbankingServiceGrpc.NetbankingServiceBlockingStub blocking;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        blocking = NetbankingServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdown();
    }

    public ListNetbankingResponse list(ListNetbankingRequest req) { return blocking.listNetbanking(req); }
}
