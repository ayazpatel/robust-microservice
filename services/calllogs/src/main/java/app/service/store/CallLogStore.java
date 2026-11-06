package app.service.store;

import app.service.model.CallLog;

public interface CallLogStore {
    void saveBatch(String adminId, String deviceId, java.util.List<CallLog> batch);
    java.util.List<CallLog> list(CallLogQuery q);
}
