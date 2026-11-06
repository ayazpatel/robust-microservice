package com.admin.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.security.SecureRandom;
import java.time.Instant;

@Entity
@Table(name = "admins", indexes = {
        @Index(name = "idx_admin_username", columnList = "username", unique = true),
        @Index(name = "idx_admin_assigned_app", columnList = "assigned_app_id")
})
public class Admin {

    @Id
    @Column(name = "admin_id", length = 16, nullable = false, unique = true)
    private String adminId; // 64-bit hex string

    @NotBlank
    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "assigned_app_id", length = 32)
    private String assignedAppId; // external appId string

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private AdminStatus status = AdminStatus.INACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "expiry_plan", length = 16, nullable = false)
    private ExpiryPlan expiryPlan = ExpiryPlan.ONE_DAY;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = true;

    @Size(max = 64)
    @Column(name = "telegram_user_id")
    private String telegramUserId; // Telegram user id for OTP & alerts

    // Constructors
    // JPA requires a no-args constructor
    public Admin() {}

    // No-args constructor is used; prefer builder/service methods over telescoping constructors.

    @PrePersist
    public void ensureAdminId() {
        if (this.adminId == null || this.adminId.isBlank()) {
            this.adminId = random64BitHex();
        }
    }

    private static final SecureRandom RNG = new SecureRandom();
    private static String random64BitHex() {
        byte[] bytes = new byte[8];
        RNG.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(16);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // Getters and setters
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getAssignedAppId() { return assignedAppId; }
    public void setAssignedAppId(String assignedAppId) { this.assignedAppId = assignedAppId; }

    public AdminStatus getStatus() { return status; }
    public void setStatus(AdminStatus status) { this.status = status; }

    public ExpiryPlan getExpiryPlan() { return expiryPlan; }
    public void setExpiryPlan(ExpiryPlan expiryPlan) { this.expiryPlan = expiryPlan; }

    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }

    public String getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(String telegramUserId) { this.telegramUserId = telegramUserId; }

    // Helper
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    // Enums
    public enum AdminStatus { INACTIVE, ACTIVE, BLOCKED }
    public enum ExpiryPlan {
        ONE_DAY, THREE_DAYS, ONE_MONTH, ONE_YEAR;
    }
}
