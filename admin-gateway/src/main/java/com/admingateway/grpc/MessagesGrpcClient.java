package com.admingateway.grpc;

import com.admingateway.grpc.messages.ListMessagesRequest;
import com.admingateway.grpc.messages.ListMessagesResponse;
import com.admingateway.grpc.messages.MessagesServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MessagesGrpcClient {
    @Value("${gateway.messages-grpc.host:localhost}") private String host;
    @Value("${gateway.messages-grpc.port:9194}") private int port;

    private ManagedChannel channel;
    private MessagesServiceGrpc.MessagesServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = MessagesServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdownNow();
    }

    public ListMessagesResponse list(ListMessagesRequest req) { return stub.listMessages(req); }
}
