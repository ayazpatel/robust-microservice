package app.service.grpc;

import app.service.grpc.messages.ListMessagesRequest;
import app.service.grpc.messages.ListMessagesResponse;
import app.service.grpc.messages.MessageItem;
import app.service.grpc.messages.MessagesServiceGrpc;
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
public class MessagesGrpcService extends MessagesServiceGrpc.MessagesServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(MessagesGrpcService.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public MessagesGrpcService(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis; this.om = om;
    }

    @Override
    public void listMessages(ListMessagesRequest request, StreamObserver<ListMessagesResponse> responseObserver) {
        String adminId = nz(request.getAdminId());
        String deviceId = request.getDeviceId();
        String appId = nz(request.getAppId()); if (appId.isBlank()) appId = "app";
        String recipientF = emptyToNull(request.getRecipient());
        String statusF = emptyToNull(request.getStatus());
        String typeF = emptyToNull(request.getType());
        long fromMs = request.getFromMs();
        long toMs = request.getToMs(); if (toMs == 0) toMs = System.currentTimeMillis();
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(request.getSize(), 200));

        ListMessagesResponse.Builder out = ListMessagesResponse.newBuilder().setPage(page).setSize(size);
        if (deviceId == null || deviceId.isBlank()) {
            responseObserver.onNext(out.setCount(0).build());
            responseObserver.onCompleted();
            return;
        }

    String prefix = "app:" + appId + ":" + adminId + ":" + deviceId;
        try {
            String json = (String) redis.opsForHash().get(prefix, "messages");
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
                String st = text(it.path("messageStatus"));
                String tp = text(it.path("messageType"));
                long ts = parseInstantMs(it.path("eventTimestamp"));
                if (recipientF != null && (rec == null || !rec.equals(recipientF))) continue;
                if (statusF != null && (st == null || !st.equalsIgnoreCase(statusF))) continue;
                if (typeF != null && (tp == null || !tp.equalsIgnoreCase(typeF))) continue;
                if (fromMs > 0 && ts > 0 && ts < fromMs) continue;
                if (toMs > 0 && ts > 0 && ts > toMs) continue;
                filtered.add(it);
            }
            int total = filtered.size();
            int start = Math.min(page * size, total);
            int end = Math.min(start + size, total);
            for (int i = start; i < end; i++) {
                JsonNode it = filtered.get(i);
                MessageItem.Builder b = MessageItem.newBuilder()
                        .setDeviceId(deviceId)
                        .setRecipient(nz(text(it.path("recipient"))))
                        .setMessageContent(nz(text(it.path("messageContent"))))
                        .setMessageStatus(nz(text(it.path("messageStatus"))))
                        .setMessageType(nz(text(it.path("messageType"))))
                        .setEventTsMs(parseInstantMs(it.path("eventTimestamp")));
                out.addItems(b);
            }
            out.setCount(total);
            responseObserver.onNext(out.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC messages list error", e);
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
 
