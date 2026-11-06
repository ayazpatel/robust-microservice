package com.admin.service;

import com.admin.controller.dto.AuthDtos;
import com.admin.model.Admin;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final AdminService adminService;
    private final OtpService otpService;
    private final TelegramBotService telegramBotService;
    private final PasswordEncoder passwordEncoder;
    private final AdminSessionService sessionService;
    private final GeoIpService geoIpService;
    @org.springframework.beans.factory.annotation.Value("${app.session.max-age-seconds:86400}")
    private long sessionTtlSeconds;
    @org.springframework.beans.factory.annotation.Value("${app.security.reset-url:}")
    private String resetUrl;

    private final AdminSocketClient adminSocketClient;

    public AuthService(AdminService adminService, OtpService otpService, TelegramBotService telegramBotService, PasswordEncoder passwordEncoder, AdminSessionService sessionService, GeoIpService geoIpService, AdminSocketClient adminSocketClient) {
        this.adminService = adminService;
        this.otpService = otpService;
        this.telegramBotService = telegramBotService;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.geoIpService = geoIpService;
        this.adminSocketClient = adminSocketClient;
    }

    @Transactional
    public AuthDtos.LoginResponse login(String username, String password, String ip, String ua) {
        AuthDtos.LoginResponse resp = new AuthDtos.LoginResponse();
        Optional<Admin> opt = adminService.findByUsername(username);
        if (opt.isEmpty()) {
            resp.status = "INVALID";
            return resp;
        }
        Admin admin = opt.get();
        if (admin.getStatus() == Admin.AdminStatus.BLOCKED) {
            resp.status = "BLOCKED";
            return resp;
        }
        if (admin.isExpired()) {
            resp.status = "EXPIRED";
            resp.adminId = admin.getAdminId();
            resp.expiresAt = admin.getExpiresAt();
            return resp;
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            resp.status = "INVALID";
            return resp;
        }
        // 2FA flow (always-on)
        if (admin.isTwoFactorEnabled()) {
            String txn = UUID.randomUUID().toString();
            String code = otpService.generate("login:" + username + ":" + txn, 300);
            telegramBotService.sendLoginOtp(admin, code);
            resp.status = "OTP_REQUIRED";
            resp.otpTxnId = txn;
            resp.adminId = admin.getAdminId();
            return resp;
        }
        // Activate on first login
        adminService.activateOnFirstLogin(admin);
        var start = sessionService.startSession(admin.getAdminId(), ip, ua);
        if (start.ipChanged) {
            telegramBotService.sendNewIpAlert(admin, ip, ua);
        }
        // Send final security notice with IP and location always
        String loc = geoIpService.lookupCityCountry(ip);
    String msg = "Login successful\nAdmin ID: " + admin.getAdminId() +
        "\nIP: " + (ip == null?"unknown":ip) + (loc!=null?" ("+loc+")":"") +
                (ua!=null?"\nUA: "+ua:"") +
                (resetUrl!=null && !resetUrl.isBlank()?"\nIf this wasn't you, reset here: "+resetUrl:"");
        telegramBotService.sendSecurityNotice(admin, msg);
    resp.status = "OK";
    resp.adminId = admin.getAdminId();
    resp.expiresAt = start.session.getCreatedAt().plusSeconds(sessionTtlSeconds);
    resp.sessionId = start.session.getSessionId();
        return resp;
    }

    @Transactional
    public AuthDtos.LoginResponse verifyOtp(String username, String txnId, String code, String ip, String ua) {
        AuthDtos.LoginResponse resp = new AuthDtos.LoginResponse();
        Optional<Admin> opt = adminService.findByUsername(username);
        if (opt.isEmpty()) { resp.status = "INVALID"; return resp; }
        Admin admin = opt.get();
        if (admin.getStatus() == Admin.AdminStatus.BLOCKED) { resp.status = "BLOCKED"; return resp; }
        if (admin.isExpired()) { resp.status = "EXPIRED"; resp.expiresAt = admin.getExpiresAt(); resp.adminId = admin.getAdminId(); return resp; }
        boolean ok = otpService.verify("login:" + username + ":" + txnId, code);
        if (!ok) { resp.status = "INVALID"; return resp; }
        adminService.activateOnFirstLogin(admin);
        var start = sessionService.startSession(admin.getAdminId(), ip, ua);
        if (start.ipChanged) {
            telegramBotService.sendNewIpAlert(admin, ip, ua);
        }
        String loc = geoIpService.lookupCityCountry(ip);
    String msg = "Login successful\nAdmin ID: " + admin.getAdminId() +
        "\nIP: " + (ip == null?"unknown":ip) + (loc!=null?" ("+loc+")":"") +
                (ua!=null?"\nUA: "+ua:"") +
                (resetUrl!=null && !resetUrl.isBlank()?"\nIf this wasn't you, reset here: "+resetUrl:"");
        telegramBotService.sendSecurityNotice(admin, msg);
    resp.status = "OK";
    resp.adminId = admin.getAdminId();
    resp.expiresAt = start.session.getCreatedAt().plusSeconds(sessionTtlSeconds);
    resp.sessionId = start.session.getSessionId();
        return resp;
    }

    @Transactional
    public AuthDtos.ResetStartResponse startReset(String username) {
        AuthDtos.ResetStartResponse resp = new AuthDtos.ResetStartResponse();
        Optional<Admin> opt = adminService.findByUsername(username);
        if (opt.isEmpty()) { resp.status = "USER_NOT_FOUND"; return resp; }
        Admin admin = opt.get();
        String txn = UUID.randomUUID().toString();
        String code = otpService.generate("reset:" + username + ":" + txn, 600);
        telegramBotService.sendLoginOtp(admin, code);
        resp.status = "SENT";
        resp.otpTxnId = txn;
        return resp;
    }

    @Transactional
    public boolean confirmReset(String username, String txnId, String code, String newPassword) {
        Optional<Admin> opt = adminService.findByUsername(username);
        if (opt.isEmpty()) return false;
        boolean ok = otpService.verify("reset:" + username + ":" + txnId, code);
        if (!ok) return false;
        Admin admin = opt.get();
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        // Warn via Telegram on successful reset
        String msg = "Password reset successful\nAdmin ID: " + admin.getAdminId() + "\nIf this wasn't you, contact the owner to recover access.";
        telegramBotService.sendSecurityNotice(admin, msg);
        return true;
    }

    public static class LogoutResult { public final boolean found; public final int closedCount; public LogoutResult(boolean f, int c){found=f;closedCount=c;} }

    @Transactional
    public LogoutResult logout(String username) {
        Optional<Admin> opt = adminService.findByUsername(username);
        if (opt.isEmpty()) return new LogoutResult(false, 0);
        Admin admin = opt.get();
        int closed = sessionService.closeAllForAdmin(admin.getAdminId());
        // Send a logout notice
        telegramBotService.sendSecurityNotice(admin, "You have been logged out from all sessions. Admin ID: " + admin.getAdminId());
        // Kick admin-socket connections
        try { adminSocketClient.kick(admin.getAdminId()); } catch (Exception ignored) {}
        return new LogoutResult(true, closed);
    }
}

