package app.service.web;

import app.service.model.ContactMin;
import app.service.store.ContactsQuery;
import app.service.store.ContactsStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/contacts")
public class ContactsApiController {
    private final ContactsStore store;
    private final SessionValidator sessionValidator;

    public ContactsApiController(ContactsStore store, SessionValidator sessionValidator) {
        this.store = store;
        this.sessionValidator = sessionValidator;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@CookieValue(value = "asid", required = false) String asid,
            @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        String sid = sidHeader != null && !sidHeader.isBlank() ? sidHeader : asid;
        var v = sessionValidator.validate(sid);
        if (!v.active()) return ResponseEntity.status(401).build();
        int ps = Math.max(1, Math.min(size, 200));

        ContactsQuery cq = new ContactsQuery();
        cq.setAdminId(v.adminId());
        cq.setDeviceId(deviceId);
        cq.setQ(q);
        cq.setPage(Math.max(0, page));
        cq.setSize(ps);

        List<ContactMin> rows = store.list(cq);
        List<Map<String, Object>> items = new ArrayList<>();
        for (var c : rows) {
            Map<String, Object> d = new HashMap<>();
            d.put("id", c.getId());
            d.put("deviceId", c.getDeviceId());
            d.put("lookupKey", c.getLookupKey());
            d.put("displayName", c.getDisplayName());
            d.put("collectedAt", c.getCollectedAt());
            d.put("numbers", c.getNumbers() == null ? List.of() : c.getNumbers());
            items.add(d);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", page);
        out.put("size", ps);
        out.put("count", items.size());
        return ResponseEntity.ok(out);
    }
}
