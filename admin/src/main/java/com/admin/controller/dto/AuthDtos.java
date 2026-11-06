package com.admin.controller.dto;

import java.time.Instant;

public class AuthDtos {
    public static class LoginRequest {
        public String username;
        public String password;
    }
    public static class LoginResponse {
        public String status; // OK, OTP_REQUIRED, BLOCKED, EXPIRED, INVALID
        public String adminId;
        public Instant expiresAt;
        public String otpTxnId;
        public String sessionId; // set when status == OK
    }
    public static class OtpVerifyRequest {
        public String username;
        public String code;
        public String otpTxnId;
    }
    public static class ResetStartRequest {
        public String username;
    }
    public static class ResetStartResponse {
        public String status; // SENT, USER_NOT_FOUND
        public String otpTxnId;
    }
    public static class ResetConfirmRequest {
        public String username;
        public String otpTxnId;
        public String code;
        public String newPassword;
    }
    public static class GenericResponse {
        public String status; // OK or ERROR
        public String message;
    }

    public static class LogoutRequest {
        public String username;
    }

    public static class LogoutResponse {
        public String status; // OK or USER_NOT_FOUND
        public int closedSessions;
    }
}

