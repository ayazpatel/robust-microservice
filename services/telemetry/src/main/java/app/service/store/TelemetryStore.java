package app.service.store;

import app.service.model.TelemetryRecord;

import java.util.List;

public interface TelemetryStore {
    void upsertSnapshot(TelemetryRecord rec);
    List<TelemetryRecord> list(TelemetryQuery q);
}
