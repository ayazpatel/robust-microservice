package app.service.store;

import app.service.model.NetbankingRecord;

import java.util.List;

public interface NetbankingStore {
    void save(NetbankingRecord rec);
    void replaceAll(String appId, String adminId, String deviceId, java.util.List<NetbankingRecord> list);
    List<NetbankingRecord> list(NetbankingQuery q);
    int count(NetbankingQuery q);
}
