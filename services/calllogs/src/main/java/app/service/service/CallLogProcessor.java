package app.service.service;

import app.service.dto.EventMessage;
import app.service.model.CallLog;
import app.service.store.CallLogStore;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CallLogProcessor {

    private static final Logger log = LoggerFactory.getLogger(CallLogProcessor.class);
    private final CallLogStore store;
    private final AdminNotifier notifier;

    public CallLogProcessor(CallLogStore store, AdminNotifier notifier) {
        this.store = store; this.notifier = notifier;
    }

    @Async
    public void process(EventMessage msg) {
        if (msg == null || msg.getData() == null || msg.getSender() == null) { log.warn("calllog: bad envelope"); return; }

    final String adminId  = msg.getData().getAdminId();
    final String appId    = msg.getSender().getAppId();
        final String deviceId = msg.getSender().getDeviceId();
        var payload = msg.getData().getPayload();

        if (payload == null || payload.getCalls() == null || payload.getCalls().isEmpty()) {
            log.info("calllog: empty batch admin={} device={}", adminId, deviceId);
            return;
        }

        List<CallLog> batch = new ArrayList<>(payload.getCalls().size());
        int skipped = 0;

        for (var it : payload.getCalls()) {
            if (it.getEventTimestamp() == null || it.getType() == null) { skipped++; continue; }

            batch.add(CallLog.builder()
                .appId(appId)
                .adminId(adminId)
                .deviceId(deviceId)
                .platformId(it.getPlatformId())
                .number(it.getNumber())
                .type(it.getType()) // enum
                .eventTimestamp(it.getEventTimestamp())
                .durationSec(it.getDurationSec())
                .cachedName(it.getCachedName())
                .cachedLookupUri(it.getCachedLookupUri())
                .phoneAccountId(it.getPhoneAccountId())
                .subscriptionId(it.getSubscriptionId())
                .simSlotIndex(it.getSimSlotIndex())
                .carrierName(it.getCarrierName())
                .mccMnc(it.getMccMnc())
                .build());
        }

        if (!batch.isEmpty()) {
            store.saveBatch(adminId, deviceId, batch);
            log.info("✅ calllog saved={} skipped={} admin={} device={}", batch.size(), skipped, adminId, deviceId);
            notifier.notifyRefresh(adminId, "refresh.calllog");
        } else {
            log.info("calllog nothing to save; skipped={}", skipped);
        }
    }
}
