package com.admin.controller;

import com.admin.model.AdminSession;
import com.admin.repository.AdminRepository;
import com.admin.repository.AdminSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/auth/session")
public class SessionApiController {

    private final AdminSessionRepository sessionRepo;
    private final AdminRepository adminRepo;

    @Value("${app.session.max-age-seconds:86400}")
    private long sessionTtlSeconds;

    public SessionApiController(AdminSessionRepository sessionRepo, AdminRepository adminRepo) {
        this.sessionRepo = sessionRepo;
        this.adminRepo = adminRepo;
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validate(HttpServletRequest request,
                                      @CookieValue(value = "asid", required = false) String asid,
                                      @RequestHeader(value = "X-Session-Id", required = false) String asidHeader) {
        String sid = asid != null ? asid : asidHeader;
        if (sid == null || sid.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("active", false));
        }
        return sessionRepo.findById(sid)
                .filter(AdminSession::isActive)
                .map(s -> {
                    // TTL check
                    Instant created = s.getCreatedAt();
                    boolean expired = created == null || created.plusSeconds(sessionTtlSeconds).isBefore(Instant.now());
                    if (expired) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                                "active", false,
                                "reason", "expired"
                        ));
                    }
                    return adminRepo.findById(s.getAdminId())
                            .map(a -> {
                                boolean userExpired = a.isExpired();
                                if (userExpired) {
                                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                                            "active", false,
                                            "reason", "user_expired",
                                            "adminId", a.getAdminId()
                                    ));
                                }
                                Instant sessionExpiry = s.getCreatedAt().plusSeconds(sessionTtlSeconds);
                                return ResponseEntity.ok(Map.of(
                                        "active", true,
                                        "adminId", a.getAdminId(),
                                        "appId", a.getAssignedAppId(),
                                        "sessionId", sid,
                                        "expiresAt", sessionExpiry
                                ));
                            })
                            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("active", false)));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("active", false)));
    }
}

