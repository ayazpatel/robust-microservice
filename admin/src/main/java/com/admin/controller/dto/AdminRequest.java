package com.admin.controller.dto;

import com.admin.model.Admin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AdminRequest {
    @NotBlank
    private String username;

    // Optional on update; required on create
    @Size(min = 8, max = 128)
    private String password;

    private String assignedAppId;

    private Admin.AdminStatus status; // defaults handled server-side

    private Admin.ExpiryPlan expiryPlan;

    private boolean twoFactorEnabled = true;

    private String telegramUserId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAssignedAppId() { return assignedAppId; }
    public void setAssignedAppId(String assignedAppId) { this.assignedAppId = assignedAppId; }

    public Admin.AdminStatus getStatus() { return status; }
    public void setStatus(Admin.AdminStatus status) { this.status = status; }

    public Admin.ExpiryPlan getExpiryPlan() { return expiryPlan; }
    public void setExpiryPlan(Admin.ExpiryPlan expiryPlan) { this.expiryPlan = expiryPlan; }

    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }

    public String getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(String telegramUserId) { this.telegramUserId = telegramUserId; }
}

