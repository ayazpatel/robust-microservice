package app.service.grpc;

import app.service.common.CallForwardReason;
import app.service.common.CallForwardServiceType;
import app.service.grpc.callforwarding.CallForwardingServiceGrpc;
import app.service.grpc.callforwarding.ListCallForwardRequest;
import app.service.grpc.callforwarding.ListCallForwardResponse;
import app.service.grpc.callforwarding.CallForwardRule;
import app.service.model.CallForward;
import app.service.store.CallForwardQuery;
import app.service.store.CallForwardStore;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class CallForwardingGrpcService extends CallForwardingServiceGrpc.CallForwardingServiceImplBase {

    private final CallForwardStore store;

    public CallForwardingGrpcService(CallForwardStore store) {
        this.store = store;
    }

    @Override
    public void listCallForwardRules(ListCallForwardRequest request,
                                     StreamObserver<ListCallForwardResponse> responseObserver) {
        try {
            CallForwardQuery q = new CallForwardQuery();
            q.setAdminId(Objects.toString(request.getAdminId(), ""));
            q.setDeviceId(request.getDeviceId());
            if (!request.getAppId().isBlank()) q.setAppId(request.getAppId());
            if (request.hasSubscriptionId()) q.setSubscriptionId(request.getSubscriptionId());
            if (!request.getReason().isBlank()) q.setReason(parseReason(request.getReason()));
            if (!request.getService().isBlank()) q.setService(parseService(request.getService()));
            q.setPage(Math.max(0, request.getPage()));
            q.setSize(Math.max(1, Math.min(200, request.getSize())));

            List<CallForward> rows = store.list(q);
        var builder = ListCallForwardResponse.newBuilder()
                    .setPage(q.getPage())
                    .setSize(q.getSize())
                    .setCount(rows.size());
            for (CallForward c : rows) builder.addItems(toProto(c));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private CallForwardRule toProto(CallForward c) {
        long ts = (c.getCollectedAt() != null ? c.getCollectedAt() : Instant.now()).toEpochMilli();
        return CallForwardRule.newBuilder()
                .setId(c.getId() == null ? 0 : c.getId())
                .setDeviceId(Objects.toString(c.getDeviceId(), ""))
                .setSubscriptionId(Objects.requireNonNullElse(c.getSubscriptionId(), 0))
                .setSimSlotIndex(Objects.requireNonNullElse(c.getSimSlotIndex(), 0))
                .setCarrierName(Objects.toString(c.getCarrierName(), ""))
                .setReason(c.getReason() == null ? "" : c.getReason().name())
                .setEnabled(c.isEnabled())
                .setForwardTo(Objects.toString(c.getForwardTo(), ""))
                .setTimeoutSeconds(Objects.requireNonNullElse(c.getTimeoutSeconds(), 0))
                .setService(c.getService() == null ? "" : c.getService().name())
                .setIsSetByAdmin(c.getIsSetByAdmin())
                .setCollectedAtMs(ts)
                .build();
    }

    private CallForwardReason parseReason(String s) {
        try { return CallForwardReason.valueOf(s.trim().toUpperCase()); } catch (Exception e) { return null; }
    }
    private CallForwardServiceType parseService(String s) {
        try { return CallForwardServiceType.valueOf(s.trim().toUpperCase()); } catch (Exception e) { return null; }
    }
}
