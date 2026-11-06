package com.admingateway.grpc;

import com.admingateway.grpc.cards.CardsServiceGrpc;
import com.admingateway.grpc.cards.ListCardsRequest;
import com.admingateway.grpc.cards.ListCardsResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CardsGrpcClient {

    @Value("${gateway.cards-grpc.host:localhost}") private String host;
    @Value("${gateway.cards-grpc.port:9199}") private int port;

    private ManagedChannel channel;
    private CardsServiceGrpc.CardsServiceBlockingStub blocking;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        blocking = CardsServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdown();
    }

    public ListCardsResponse list(ListCardsRequest req) {
        return blocking.listCards(req);
    }
}
