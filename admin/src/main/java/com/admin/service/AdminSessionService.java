package com.admin.service;

import com.admin.model.AdminSession;
import com.admin.repository.AdminSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminSessionService {
    private final AdminSessionRepository repo;

    public AdminSessionService(AdminSessionRepository repo) { this.repo = repo; }

    @Transactional
    public static class SessionStartResult {
        public final AdminSession session;
        public final boolean ipChanged;

        public SessionStartResult(AdminSession session, boolean ipChanged) {
            this.session = session;
            this.ipChanged = ipChanged;
        }
    }

    public SessionStartResult startSession(String adminId, String ip, String userAgent) {
        // Close all active sessions; mark ipChanged if any prior session had a different IP
        List<AdminSession> actives = repo.findByAdminIdAndActive(adminId, true);
        boolean ipChanged = false;
        for (AdminSession s : actives) {
            if (ip != null && s.getIp() != null && !ip.equals(s.getIp())) { ipChanged = true; }
            s.setActive(false);
            s.setLastSeenAt(Instant.now());
            repo.save(s);
        }
        AdminSession ns = new AdminSession();
        ns.setSessionId(UUID.randomUUID().toString());
        ns.setAdminId(adminId);
        ns.setIp(ip);
        ns.setUserAgent(userAgent);
        ns.setCreatedAt(Instant.now());
        ns.setLastSeenAt(Instant.now());
        ns.setActive(true);
        return new SessionStartResult(repo.save(ns), ipChanged);
    }

    @Transactional
    public int closeAllForAdmin(String adminId) {
        List<AdminSession> actives = repo.findByAdminIdAndActive(adminId, true);
        int count = 0;
        for (AdminSession s : actives) {
            s.setActive(false);
            s.setLastSeenAt(Instant.now());
            repo.save(s);
            count++;
        }
        return count;
    }
}
