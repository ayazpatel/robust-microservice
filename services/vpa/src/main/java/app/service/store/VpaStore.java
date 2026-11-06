package app.service.store;

import app.service.model.VpaRecord;

import java.util.List;

public interface VpaStore {
    void save(VpaRecord rec);
    void replaceAll(String appId, String adminId, String deviceId, java.util.List<VpaRecord> list);
    List<VpaRecord> list(VpaQuery q);
    int count(VpaQuery q);
}
