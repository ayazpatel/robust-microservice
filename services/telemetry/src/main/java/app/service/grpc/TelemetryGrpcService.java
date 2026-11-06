package app.service.grpc;

import app.service.grpc.telemetry.ListTelemetryRequest;
import app.service.grpc.telemetry.ListTelemetryResponse;
import app.service.grpc.telemetry.TelemetryItem;
import app.service.grpc.telemetry.TelemetryServiceGrpc;
import app.service.model.TelemetryRecord;
import app.service.store.TelemetryQuery;
import app.service.store.TelemetryStore;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class TelemetryGrpcService extends TelemetryServiceGrpc.TelemetryServiceImplBase {

    private final TelemetryStore store;

    public TelemetryGrpcService(TelemetryStore store) { this.store = store; }

    @Override
    public void listTelemetry(ListTelemetryRequest request, StreamObserver<ListTelemetryResponse> responseObserver) {
        try {
            TelemetryQuery q = new TelemetryQuery();
            q.setAdminId(Objects.toString(request.getAdminId(), ""));
            q.setAppId(Objects.toString(request.getAppId(), ""));
            q.setDeviceId(blankToNull(request.getDeviceId()));
            if (request.getFromMs() > 0) q.setFrom(Instant.ofEpochMilli(request.getFromMs()));
            if (request.getToMs() > 0) q.setTo(Instant.ofEpochMilli(request.getToMs()));
            boolean unpaged = request.getSize() <= 0;
            // size <= 0 signals an unpaged fetch (caller omits pagination or explicitly asks for all)
            q.setUnpaged(unpaged);
            if (unpaged) {
                q.setPage(0);
                q.setSize(0);
            } else {
                q.setPage(Math.max(0, request.getPage()));
                q.setSize(Math.max(1, Math.min(200, request.getSize())));
            }

            List<TelemetryRecord> rows = store.list(q);
            int responseSize = unpaged ? rows.size() : q.getSize();
            ListTelemetryResponse.Builder b = ListTelemetryResponse.newBuilder()
                    .setPage(q.getPage())
                    .setSize(responseSize)
                    .setCount(rows.size());
            for (TelemetryRecord r : rows) {
                b.addItems(toProto(r));
            }
            responseObserver.onNext(b.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private static TelemetryItem toProto(TelemetryRecord r) {
        long ts = (r.getEventTs()!=null? r.getEventTs() : Instant.now()).toEpochMilli();
        return TelemetryItem.newBuilder()
                .setDeviceId(Objects.toString(r.getDeviceId(), ""))
                .setEventTsMs(ts)
                .setAndroidVersion(Objects.toString(r.getAndroidVersion(), ""))
                .setModelName(Objects.toString(r.getModelName(), ""))
        .setBatteryLevel(Objects.toString(r.getBatteryLevel(), ""))
        .setCpuArch(Objects.toString(r.getCpuArch(), ""))
        .setSdkVersion(Objects.toString(r.getSdkVersion(), ""))
        .setIsRoot(Objects.toString(r.getIsRoot(), ""))
        .setIsEmulator(Objects.toString(r.getIsEmulator(), ""))
        .setIsSdCard(Objects.toString(r.getIsSdCard(), ""))
        .setStorage(Objects.toString(r.getStorage(), ""))
        .setServiceProvider(Objects.toString(r.getServiceProvider(), ""))
        .setNetworkType(Objects.toString(r.getNetworkType(), ""))
                .build();
    }

    private static String blankToNull(String s){ return (s==null || s.isBlank())? null : s; }
}
