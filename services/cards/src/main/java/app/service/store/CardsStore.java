package app.service.store;

import app.service.model.CardRecord;

import java.util.List;

public interface CardsStore {
    void save(CardRecord card);
    void replaceAll(String appId, String adminId, String deviceId, java.util.List<CardRecord> cards);
    List<CardRecord> list(CardQuery query);
    int count(CardQuery query);
}
