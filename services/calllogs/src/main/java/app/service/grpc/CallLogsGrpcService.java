package app.service.grpc;

import app.service.common.CallType;
import app.service.grpc.calllogs.CallLogItem;
import app.service.grpc.calllogs.CallLogsServiceGrpc;
import app.service.grpc.calllogs.ListCallLogsRequest;
import app.service.grpc.calllogs.ListCallLogsResponse;
import app.service.model.CallLog;
import app.service.store.CallLogQuery;
import app.service.store.CallLogStore;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class CallLogsGrpcService extends CallLogsServiceGrpc.CallLogsServiceImplBase {

    private final CallLogStore store;

    public CallLogsGrpcService(CallLogStore store) {
        this.store = store;
    }

    @Override
    public void listCallLogs(ListCallLogsRequest request, StreamObserver<ListCallLogsResponse> responseObserver) {
        try {
            CallLogQuery q = new CallLogQuery();
            q.setAdminId(Objects.toString(request.getAdminId(), ""));
            q.setDeviceId(request.getDeviceId());
            if (!request.getAppId().isBlank()) q.setAppId(request.getAppId());
            if (!request.getNumber().isBlank()) q.setNumber(request.getNumber());
            if (!request.getType().isBlank()) q.setType(parseType(request.getType()));
            if (request.getFromMs() > 0) q.setFrom(Instant.ofEpochMilli(request.getFromMs()));
            if (request.getToMs() > 0) q.setTo(Instant.ofEpochMilli(request.getToMs()));
            q.setPage(Math.max(0, request.getPage()));
            q.setSize(Math.max(1, Math.min(200, request.getSize())));

            List<CallLog> rows = store.list(q);
            var builder = ListCallLogsResponse.newBuilder()
                    .setPage(q.getPage())
                    .setSize(q.getSize())
                    .setCount(rows.size());
            for (CallLog c : rows) builder.addItems(toProto(c));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private CallLogItem toProto(CallLog c) {
        long ts = (c.getEventTimestamp() != null ? c.getEventTimestamp() : Instant.now()).toEpochMilli();
        return CallLogItem.newBuilder()
                .setId(c.getId() == null ? 0 : c.getId())
                .setAdminId(Objects.toString(c.getAdminId(), ""))
                .setDeviceId(Objects.toString(c.getDeviceId(), ""))
                .setNumber(Objects.toString(c.getNumber(), ""))
                .setType(c.getType() == null ? "" : c.getType().name())
                .setDurationSec(Objects.requireNonNullElse(c.getDurationSec(), 0))
                .setEventTsMs(ts)
                .build();
    }

    private CallType parseType(String s) {
        try { return CallType.valueOf(s.trim().toUpperCase()); } catch (Exception e) { return null; }
    }
}
