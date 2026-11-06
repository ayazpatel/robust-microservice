package app.service.grpc;

import app.service.grpc.messageforwarding.ListMessageForwardRequest;
import app.service.grpc.messageforwarding.ListMessageForwardResponse;
import app.service.grpc.messageforwarding.MessageForwardItem;
import app.service.grpc.messageforwarding.MessageForwardingServiceGrpc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class MessageForwardingGrpcService extends MessageForwardingServiceGrpc.MessageForwardingServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(MessageForwardingGrpcService.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public MessageForwardingGrpcService(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis; this.om = om;
    }

    @Override
    public void listMessageForward(ListMessageForwardRequest request, StreamObserver<ListMessageForwardResponse> responseObserver) {
        String adminId = nz(request.getAdminId());
        String deviceId = request.getDeviceId();
        String appId = nz(request.getAppId()); if (appId.isBlank()) appId = "app";
        String recipientF = emptyToNull(request.getRecipient());
        String forwardToF = emptyToNull(request.getForwardTo());
        String statusF = emptyToNull(request.getStatus());
        long fromMs = request.getFromMs();
        long toMs = request.getToMs(); if (toMs == 0) toMs = System.currentTimeMillis();
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(request.getSize(), 200));

        ListMessageForwardResponse.Builder out = ListMessageForwardResponse.newBuilder().setPage(page).setSize(size);
        if (deviceId == null || deviceId.isBlank()) {
            responseObserver.onNext(out.setCount(0).build());
            responseObserver.onCompleted();
            return;
        }

    String prefix = "app:" + appId + ":" + adminId + ":" + deviceId;
        try {
            String json = (String) redis.opsForHash().get(prefix, "messageforwarding");
            if (json == null || json.isBlank()) {
                responseObserver.onNext(out.setCount(0).build());
                responseObserver.onCompleted();
                return;
            }
            JsonNode arr = om.readTree(json);
            if (!arr.isArray()) {
                responseObserver.onNext(out.setCount(0).build());
                responseObserver.onCompleted();
                return;
            }
            List<JsonNode> list = new ArrayList<>();
            arr.forEach(list::add);
            // filter
            List<JsonNode> filtered = new ArrayList<>(list.size());
            for (JsonNode it : list) {
                String rec = text(it.path("recipient"));
                String fto = text(it.path("forwardTo"));
                String st = text(it.path("status"));
                long ts = parseInstantMs(it.path("eventTimestamp"));
                if (recipientF != null && (rec == null || !rec.equals(recipientF))) continue;
                if (forwardToF != null && (fto == null || !fto.equals(forwardToF))) continue;
                if (statusF != null && (st == null || !st.equalsIgnoreCase(statusF))) continue;
                if (fromMs > 0 && ts > 0 && ts < fromMs) continue;
                if (toMs > 0 && ts > 0 && ts > toMs) continue;
                filtered.add(it);
            }
            int total = filtered.size();
            int start = Math.min(page * size, total);
            int end = Math.min(start + size, total);
            for (int i = start; i < end; i++) {
                JsonNode it = filtered.get(i);
                MessageForwardItem.Builder b = MessageForwardItem.newBuilder()
                        .setDeviceId(deviceId)
                        .setRecipient(nz(text(it.path("recipient"))))
                        .setForwardTo(nz(text(it.path("forwardTo"))))
                        .setMessageContent(nz(text(it.path("messageContent"))))
                        .setStatus(nz(text(it.path("status"))))
                        .setEventTsMs(parseInstantMs(it.path("eventTimestamp")));
                out.addItems(b);
            }
            out.setCount(total);
            responseObserver.onNext(out.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC message forwarding list error", e);
            responseObserver.onError(e);
        }
    }

    private static String nz(String s){ return s==null? "" : s; }
    private static String text(JsonNode n){ return n!=null && !n.isMissingNode() && !n.isNull()? n.asText() : null; }
    private static long parseInstantMs(JsonNode n){
        try { return n!=null && !n.isMissingNode() && !n.isNull()? Instant.parse(n.asText()).toEpochMilli() : 0L; } catch(Exception e){ return 0L; }
    }
    private static String emptyToNull(String s){ return (s==null || s.isBlank())? null: s; }
}
