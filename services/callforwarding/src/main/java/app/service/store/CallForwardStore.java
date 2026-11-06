package app.service.store;

import app.service.model.CallForward;

import java.time.Instant;
import java.util.List;

public interface CallForwardStore {
    void upsertSnapshot(String adminId, String deviceId, Instant collectedAt, List<CallForward> rules);
    List<CallForward> list(CallForwardQuery q);
}
