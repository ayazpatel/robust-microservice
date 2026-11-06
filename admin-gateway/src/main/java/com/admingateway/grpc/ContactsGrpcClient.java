package com.admingateway.grpc;

import com.admingateway.grpc.contacts.ContactsServiceGrpc;
import com.admingateway.grpc.contacts.ListContactsRequest;
import com.admingateway.grpc.contacts.ListContactsResponse;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContactsGrpcClient {
    @Value("${gateway.contacts-grpc.host:localhost}") private String host;
    @Value("${gateway.contacts-grpc.port:9192}") private int port;

    private ManagedChannel channel;
    private ContactsServiceGrpc.ContactsServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = ContactsServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdownNow();
    }

    public ListContactsResponse list(ListContactsRequest req) { return stub.listContacts(req); }
}
