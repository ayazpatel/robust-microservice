package com.admin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admin_sessions", indexes = {
        @Index(name = "idx_admin_session_admin", columnList = "admin_id"),
        @Index(name = "idx_admin_session_active", columnList = "admin_id,active")
})
public class AdminSession {
    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId; // uuid

    @Column(name = "admin_id", length = 16, nullable = false)
    private String adminId;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
