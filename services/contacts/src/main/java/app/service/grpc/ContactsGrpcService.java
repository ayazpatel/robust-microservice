package app.service.grpc;

import app.service.grpc.contacts.ListContactsRequest;
import app.service.grpc.contacts.ListContactsResponse;
import app.service.grpc.contacts.ContactsServiceGrpc;
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
public class ContactsGrpcService extends ContactsServiceGrpc.ContactsServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(ContactsGrpcService.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public ContactsGrpcService(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis;
        this.om = om;
    }

    @Override
    public void listContacts(ListContactsRequest request, StreamObserver<ListContactsResponse> responseObserver) {
        String adminId = nz(request.getAdminId());
        String deviceId = request.getDeviceId();
        String appId = nz(request.getAppId());
        if (appId.isBlank()) appId = "app";
        int page = Math.max(0, request.getPage());
        int size = Math.max(1, Math.min(request.getSize(), 200));
        String q = request.getQ();

    ListContactsResponse.Builder out = ListContactsResponse.newBuilder()
                .setPage(page)
                .setSize(size);

        if (deviceId == null || deviceId.isBlank()) {
            // unified model requires deviceId for contacts
            responseObserver.onNext(out.setCount(0).build());
            responseObserver.onCompleted();
            return;
        }

    String key = "app:" + appId + ":" + adminId + ":" + deviceId;
        try {
            String json = (String) redis.opsForHash().get(key, "contacts");
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
            List<JsonNode> items = new ArrayList<>();
            arr.forEach(items::add);
            int total = items.size();
            int start = Math.min(page * size, total);
            int end = Math.min(start + size, total);
            for (int i = start; i < end; i++) {
                JsonNode it = items.get(i);
                String lookup = text(it.path("lookupKey"));
                String name = text(it.path("displayName"));
        app.service.grpc.contacts.ContactItem.Builder b = app.service.grpc.contacts.ContactItem.newBuilder()
                        .setAdminId(adminId)
                        .setDeviceId(deviceId)
                        .setLookupKey(nz(lookup))
                        .setDisplayName(nz(name))
                        .setCollectedAtMs(Instant.now().toEpochMilli());
                if (lookup != null && !lookup.isBlank()) {
                    b.setId(computeId(adminId, deviceId, lookup));
                }
                JsonNode nums = it.path("numbers");
                if (nums.isArray()) {
                    for (JsonNode n : nums) {
                        if (n != null && !n.isNull()) b.addNumbers(n.asText());
                    }
                }
                if (matches(name, q)) {
                    out.addItems(b);
                }
            }
            out.setCount(out.getItemsCount());
            responseObserver.onNext(out.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC contacts list error", e);
            responseObserver.onError(e);
        }
    }

    private static boolean matches(String displayName, String q) {
        if (q == null || q.isBlank()) return true;
        return displayName != null && displayName.toLowerCase().contains(q.toLowerCase());
    }

    private static String text(JsonNode n) { return n != null && !n.isMissingNode() && !n.isNull() ? n.asText() : null; }
    private static String nz(String s){ return s==null? "" : s; }

    private static long computeId(String adminId, String deviceId, String lookupKey) {
        long h = 1125899906842597L;
        String s = adminId + '|' + deviceId + '|' + lookupKey;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return h < 0 ? -h : h;
    }
}
 
