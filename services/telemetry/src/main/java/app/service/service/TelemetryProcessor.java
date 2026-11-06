package app.service.service;

import app.service.dto.EventMessage;
import app.service.model.InstalledAppEntity;
import app.service.model.SimInfoEntity;
import app.service.model.TelemetryRecord;
import app.service.store.TelemetryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TelemetryProcessor {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProcessor.class);

    private final TelemetryStore store;
    private final AdminNotifier notifier;

    public TelemetryProcessor(TelemetryStore store, AdminNotifier notifier) {
        this.store = store;
        this.notifier = notifier;
    }

    @Async   // runs on spring.task.execution pool; config via application.yml
    public void process(EventMessage msg) {
        try {
            if (msg == null) { log.warn("Null message"); return; }
            if (msg.getData() == null) { log.warn("Message missing data; topic={}", msg.getTopic()); return; }
            if (msg.getSender() == null) { log.warn("Message missing sender; topic={}", msg.getTopic()); return; }

            String adminIdRaw = msg.getData().getAdminId();
            Long adminId = parseAdminId(adminIdRaw); // logs only; storage uses raw string
            String deviceId = msg.getSender().getDeviceId();
            String appId = msg.getSender().getAppId();
            // Harden: ensure appId is never null/blank so Redis keys don't land under empty partition
            String safeAppId = (appId != null && !appId.isBlank()) ? appId : "unknown";
            if (!safeAppId.equals(appId)) {
                log.warn("Telemetry missing appId from sender; using fallback 'unknown' device={} admin={}", deviceId, adminId);
            }
            if (deviceId == null || deviceId.isBlank()) { log.warn("Missing deviceId; topic={} admin={}", msg.getTopic(), adminId); return; }
            Instant eventTs = Instant.ofEpochMilli(msg.getTs());
            if (log.isDebugEnabled()) {
                log.debug("Received telemetry topic={} admin={} appId={} device={} ts={}", msg.getTopic(), adminId, appId, deviceId, eventTs);
            }

            
            var p = msg.getData().getPayload();
            if (p == null) { log.warn("Missing payload; topic={} device={} admin={}", msg.getTopic(), deviceId, adminId); return; }

            TelemetryRecord rec = new TelemetryRecord();
            rec.setAdminId(adminIdRaw != null ? adminIdRaw : "");
            // Partition by application just like messages service does; always non-empty
            rec.setAppId(safeAppId);
            rec.setDeviceId(deviceId);
            rec.setEventTs(eventTs);

            rec.setCpuArch(p.getCup_arch());
            rec.setSdkVersion(p.getSdkVersion());
            rec.setAndroidVersion(p.getAndroidVersion());
            rec.setModelName(p.getModelName());
            rec.setIsRoot(p.getIsRoot());
            rec.setIsEmulator(p.getIsEmulator());
            rec.setIsSdCard(p.getIsSdCard());
            rec.setStorage(p.getStorage());
            rec.setServiceProvider(p.getService_provider());
            rec.setNetworkType(p.getNetwork_type());
            rec.setBatteryLevel(p.getBattery_level());

            if (rec.getSims() != null) rec.getSims().clear();
            if (p.getSims() != null) {
                p.getSims().forEach(s -> {
                    SimInfoEntity e = new SimInfoEntity();
                    e.setSimSlot(s.getSim_slot());
                    e.setSimOperator(s.getSim_operator());
                    e.setSimState(s.getSim_state());
                    e.setPhoneNumber(s.getPhone_number());
                    e.setCountryIso(s.getCountry_iso());
                    rec.addSim(e);
                });
            }

            if (rec.getInstalledApps() != null) rec.getInstalledApps().clear();
            if (p.getInstalled_apps() != null) {
                p.getInstalled_apps().forEach(a -> {
                    InstalledAppEntity e = new InstalledAppEntity();
                    e.setAppName(a.getApp_name());
                    e.setPackageName(a.getPackage_name());
                    e.setVersionName(a.getVersion_name());
                    rec.addApp(e);
                });
            }

            store.upsertSnapshot(rec);
            log.info("✅ saved telemetry admin={} appId={} device={} ts={}", adminId, safeAppId, deviceId, eventTs);

            // notify admin socket (optional)
            notifier.notifyRefresh(adminIdRaw, "refresh.telemetry");

        } catch (Exception ex) {
            log.error("Failed processing telemetry", ex);
            // let transaction rollback; Rabbit auto-ack since we rely on idempotency and logging
        }
    }

    private Long parseAdminId(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException nfe) {
            // Support hex-like ids (up to 16 hex chars fit into signed 64-bit)
            String s = raw.trim();
            if (s.matches("(?i)^[0-9a-f]{1,16}$")) {
                try {
                    return Long.parseUnsignedLong(s, 16);
                } catch (Exception ignored) {
                    // ignore parse error; will fallback to hash
                }
            }
            // Fallback: hash to 64-bit stable value
            long h = 1125899906842597L; // prime seed
            for (int i = 0; i < s.length(); i++) {
                h = 31 * h + s.charAt(i);
            }
            // Ensure positive
            if (h < 0) h = -h;
            return h;
        }
    }
}








