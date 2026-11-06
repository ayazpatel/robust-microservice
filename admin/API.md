# Admin Service REST API

Base URL: http://<host>:8082

Auth: HTTP Basic form login for CEO on /login (browser). For REST endpoints under /api/**, CEO role is required per Spring Security.

Headers:
- User-Agent: optional, user agent string (used by /auth endpoints for session tracking)

---

## Admins API (/api/admins)

- GET /api/admins
  - Description: List all admins
  - Auth: Requires CEO role
  - Response: 200 application/json
    - Body: Array<AdminResponse>
      - adminId: string
      - username: string
      - assignedAppId: string|null
      - status: "INACTIVE" | "ACTIVE" | "BLOCKED"
      - expiryPlan: "ONE_DAY" | "THREE_DAYS" | "ONE_MONTH" | "ONE_YEAR"
      - activatedAt: ISO-8601 timestamp|null
      - expiresAt: ISO-8601 timestamp|null
      - twoFactorEnabled: boolean (always true)
      - telegramUserId: string|null

- GET /api/admins/{id}
  - Description: Get one admin by id
  - Auth: Requires CEO role
  - Response:
    - 200 application/json: AdminResponse
    - 404 if not found

- POST /api/admins
  - Description: Create a new admin
  - Auth: Requires CEO role
  - Request: application/json AdminRequest
    - username: string (required)
    - password: string (required on create; min 8)
    - assignedAppId: string|null
    - status: AdminStatus (optional; panel enforces INACTIVE initially)
    - expiryPlan: ExpiryPlan (defaults to ONE_DAY if omitted)
    - twoFactorEnabled: boolean (ignored; always true)
    - telegramUserId: string|null
  - Response:
    - 201 Created with Location: /api/admins/{adminId}
    - Body: AdminResponse

- PUT /api/admins/{id}
  - Description: Update an existing admin
  - Auth: Requires CEO role
  - Request: application/json AdminRequest
    - username: string
    - password: string (optional; >= 8 to change)
    - assignedAppId: string|null
    - status: AdminStatus (limited transitions: ACTIVE -> BLOCKED; BLOCKED -> ACTIVE; INACTIVE -> BLOCKED)
    - expiryPlan: ExpiryPlan
    - twoFactorEnabled: boolean (ignored; always true)
    - telegramUserId: string|null
  - Response:
    - 200 application/json: AdminResponse
    - 404 if not found

- DELETE /api/admins/{id}
  - Description: Delete an admin
  - Auth: Requires CEO role
  - Response: 204 No Content

---

## Auth API (/auth)
Note: Used for login/otp/reset/logout flows and to track sessions. Rate-limited with a simple key-based limiter.

- POST /auth/login
  - Description: Start login; if password valid and 2FA is enabled, OTP is sent via Telegram and OTP_REQUIRED is returned.
  - Headers (optional):
    - User-Agent: used for session logging
  - Request: application/json LoginRequest
    - username: string
    - password: string
  - Response: 200 application/json LoginResponse
    - status: "OTP_REQUIRED" | "OK" | "BLOCKED" | "EXPIRED" | "INVALID"
    - adminId: string|null
    - expiresAt: ISO-8601|null
    - otpTxnId: string|null (when OTP_REQUIRED)

- POST /auth/verify-otp
  - Description: Verify OTP and finalize login/session. This always sends a “Login successful” Telegram security notice that includes Admin ID, IP (server-derived), optional location (when GeoIP is enabled), and User-Agent. If the login originates from a different IP than existing active sessions, a separate “New login from IP …” alert is sent and existing sessions are closed.
  - Headers (optional): User-Agent
  - Request: application/json OtpVerifyRequest
    - username: string
    - code: string
    - otpTxnId: string
  - Response: 200 application/json LoginResponse
    - status: "OK" | "BLOCKED" | "EXPIRED" | "INVALID"
    - adminId: string|null
    - expiresAt: ISO-8601|null

- POST /auth/reset/start
  - Description: Start password reset; sends OTP via Telegram when user exists.
  - Request: application/json ResetStartRequest
    - username: string
  - Response: 200 application/json ResetStartResponse
    - status: "SENT" | "USER_NOT_FOUND"
    - otpTxnId: string|null

- POST /auth/reset/confirm
  - Description: Confirm password reset with OTP and set new password. On success, a Telegram warning is sent: it includes the Admin ID and a "contact owner to recover access" note.
  - Request: application/json ResetConfirmRequest
    - username: string
    - otpTxnId: string
    - code: string
    - newPassword: string (>= 8)
  - Response: 200 application/json GenericResponse
    - status: "OK" | "ERROR"
    - message: string

- POST /auth/logout
  - Description: Log out the specified admin by clearing all active sessions. Sends a confirmation notice via Telegram. Use this to invalidate current access.
  - Request: application/json LogoutRequest
    - username: string
  - Response: 200 application/json LogoutResponse
    - status: "OK" | "USER_NOT_FOUND"
    - closedSessions: number

---

## Security
- All /api/** endpoints require CEO role and are protected by Spring Security.
- Form-based login at /login for browser access; GET /logout available for convenience.
- 2FA is compulsory; OTPs and security alerts are sent to each admin’s Telegram user ID using a global bot token configured via `telegram.bot.token`.
- Session policy: On every successful login, any existing active sessions for that admin are closed and a fresh session starts. If the prior sessions were from a different IP, a separate “New login from IP …” alert is sent.
- Security notices:
  - Login success notice includes Admin ID, IP, optional location (when enabled), and User-Agent; can include a reset URL if configured.
  - Password reset success triggers a warning with Admin ID and "contact owner to recover access".

## Models
- AdminStatus: INACTIVE, ACTIVE, BLOCKED
- ExpiryPlan: ONE_DAY, THREE_DAYS, ONE_MONTH, ONE_YEAR

## Notes
- IP address is derived from the HTTP request (server-side); no need to pass an IP header.
- To receive OTP/security messages, the admin must have a valid `telegramUserId`, and the user must have started the bot (press Start in Telegram) so a chat exists. If you see "Bad Request: chat not found", verify the user ID and that the bot has been started.
- Ensure `telegram.bot.token` is configured. Optional: enable GeoIP via `geo.lookup.enabled=true` to include city/region/country in login notices. Optional: provide `app.security.reset-url` to include a quick-reset link in notices.
