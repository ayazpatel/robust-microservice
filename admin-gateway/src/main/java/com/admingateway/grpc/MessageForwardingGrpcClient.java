package com.admingateway.grpc;

import com.admingateway.grpc.messageforwarding.ListMessageForwardRequest;
import com.admingateway.grpc.messageforwarding.ListMessageForwardResponse;
import com.admingateway.grpc.messageforwarding.MessageForwardingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MessageForwardingGrpcClient {

    @Value("${gateway.messageforwarding-grpc.host:localhost}") private String host;
    @Value("${gateway.messageforwarding-grpc.port:9196}") private int port;

    private ManagedChannel channel;
    private MessageForwardingServiceGrpc.MessageForwardingServiceBlockingStub blocking;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        blocking = MessageForwardingServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdown();
    }

    public ListMessageForwardResponse list(ListMessageForwardRequest req) {
        return blocking.listMessageForward(req);
    }
}
