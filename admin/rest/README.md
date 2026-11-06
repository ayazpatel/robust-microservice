# Admin Auth API – REST Client Collection

This folder contains VS Code REST Client (.http) files to exercise the Admin service Auth API.

Requirements:
- Install the REST Client extension (humao.rest-client)
- Admin service running at http://localhost:8082 (adjust `@base` if different)
- Telegram bot token configured for the service (telegram.bot.token) and each admin has a `telegramUserId`

Files:
- `auth.http` – Endpoints:
  - POST /auth/login
  - POST /auth/verify-otp
  - POST /auth/reset/start
  - POST /auth/reset/confirm

Usage:
1) Open `auth.http` in VS Code.
2) Edit the variables at the top (username/password/ip/ua). Leave `@otpTxnId` and `@resetTxnId` blank—they’ll be set from prior responses.
3) Click `Send Request` above a request to execute.
4) For OTP, check Telegram for the code, update `@otpCode`, then run Verify.

Notes:
- The login request test script saves `otpTxnId` automatically when `OTP_REQUIRED` is returned.
- Session tracking uses `X-Forwarded-For` and `User-Agent` headers. A new login from a different IP will close older active sessions and send a new-IP alert via Telegram.
