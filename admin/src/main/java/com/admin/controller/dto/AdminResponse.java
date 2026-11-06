package com.admin.controller.dto;

import com.admin.model.Admin;

import java.time.Instant;

public class AdminResponse {
    private String adminId;
    private String username;
    private String assignedAppId;
    private Admin.AdminStatus status;
    private Admin.ExpiryPlan expiryPlan;
    private Instant activatedAt;
    private Instant expiresAt;
    private boolean twoFactorEnabled;
    private String telegramUserId;

    public static AdminResponse from(Admin a) {
        AdminResponse r = new AdminResponse();
        r.adminId = a.getAdminId();
        r.username = a.getUsername();
        r.assignedAppId = a.getAssignedAppId();
        r.status = a.getStatus();
        r.expiryPlan = a.getExpiryPlan();
        r.activatedAt = a.getActivatedAt();
        r.expiresAt = a.getExpiresAt();
        r.twoFactorEnabled = a.isTwoFactorEnabled();
        r.telegramUserId = a.getTelegramUserId();
        return r;
    }

    // Getters
    public String getAdminId() { return adminId; }
    public String getUsername() { return username; }
    public String getAssignedAppId() { return assignedAppId; }
    public Admin.AdminStatus getStatus() { return status; }
    public Admin.ExpiryPlan getExpiryPlan() { return expiryPlan; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public String getTelegramUserId() { return telegramUserId; }
}

