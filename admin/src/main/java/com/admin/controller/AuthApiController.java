package com.admin.controller;

import com.admin.controller.dto.AuthDtos;
import com.admin.service.AuthService;
import com.admin.service.RateLimiterService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

@RestController
@RequestMapping("/auth")
public class AuthApiController {

    private final AuthService authService;
    private final RateLimiterService limiter;

    public AuthApiController(AuthService authService, RateLimiterService limiter) {
        this.authService = authService;
        this.limiter = limiter;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(@RequestBody AuthDtos.LoginRequest req,
                                                       @RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                                       @RequestHeader(value = "User-Agent", required = false) String ua,
                                                       HttpServletRequest request) {
        String key = "login:" + (req.username == null ? "" : req.username.toLowerCase());
        if (!limiter.tryConsume(key, 10, 60)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String ip = request.getRemoteAddr();
        AuthDtos.LoginResponse resp = authService.login(req.username, req.password, ip, ua);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthDtos.LoginResponse> verifyOtp(@RequestBody AuthDtos.OtpVerifyRequest req,
                                                            @RequestHeader(value = "User-Agent", required = false) String ua,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        String key = "verify:" + (req.username == null ? "" : req.username.toLowerCase());
        if (!limiter.tryConsume(key, 20, 60)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String ip = request.getRemoteAddr();
        AuthDtos.LoginResponse resp = authService.verifyOtp(req.username, req.otpTxnId, req.code, ip, ua);
        // On success, set HttpOnly session cookie (asid) for 24h by default
        if ("OK".equals(resp.status) && resp.sessionId != null && !resp.sessionId.isBlank()) {
            // Default 24h; configurable via env later if needed
            ResponseCookie cookie = ResponseCookie.from("asid", resp.sessionId)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(24L * 60L * 60L)
                    .build();
            response.addHeader("Set-Cookie", cookie.toString());
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/reset/start")
    public ResponseEntity<AuthDtos.ResetStartResponse> resetStart(@RequestBody AuthDtos.ResetStartRequest req) {
        String key = "resetstart:" + (req.username == null ? "" : req.username.toLowerCase());
        if (!limiter.tryConsume(key, 5, 300)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return ResponseEntity.ok(authService.startReset(req.username));
    }

    @PostMapping("/reset/confirm")
    public ResponseEntity<AuthDtos.GenericResponse> resetConfirm(@RequestBody AuthDtos.ResetConfirmRequest req) {
        AuthDtos.GenericResponse resp = new AuthDtos.GenericResponse();
        if (req.newPassword == null || req.newPassword.length() < 8) {
            resp.status = "ERROR"; resp.message = "Password too short"; return ResponseEntity.badRequest().body(resp);
        }
        boolean ok = authService.confirmReset(req.username, req.otpTxnId, req.code, req.newPassword);
        resp.status = ok ? "OK" : "ERROR";
        resp.message = ok ? "Password reset" : "Invalid or expired code";
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthDtos.LogoutResponse> logout(@RequestBody AuthDtos.LogoutRequest req,
                                                          HttpServletResponse response) {
        String key = "logout:" + (req.username == null ? "" : req.username.toLowerCase());
        if (!limiter.tryConsume(key, 20, 60)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        AuthDtos.LogoutResponse out = new AuthDtos.LogoutResponse();
        if (req.username == null || req.username.isBlank()) { out.status = "USER_NOT_FOUND"; return ResponseEntity.ok(out); }
        var result = authService.logout(req.username);
        out.status = result.found ? "OK" : "USER_NOT_FOUND";
        out.closedSessions = result.closedCount;
        // Clear session cookie client-side
        ResponseCookie cookie = ResponseCookie.from("asid", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(out);
    }
}

