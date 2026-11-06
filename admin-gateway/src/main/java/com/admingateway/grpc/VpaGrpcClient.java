package com.admingateway.grpc;

import com.admingateway.grpc.vpa.ListVpaRequest;
import com.admingateway.grpc.vpa.ListVpaResponse;
import com.admingateway.grpc.vpa.VpaServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VpaGrpcClient {

    @Value("${gateway.vpa-grpc.host:localhost}") private String host;
    @Value("${gateway.vpa-grpc.port:9197}") private int port;

    private ManagedChannel channel;
    private VpaServiceGrpc.VpaServiceBlockingStub blocking;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        blocking = VpaServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdown();
    }

    public ListVpaResponse list(ListVpaRequest req) { return blocking.listVpa(req); }
}
