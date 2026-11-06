Admin Socket (Minimal)

What it is
- Tiny uWebSockets.js service for the admin panel.
- Accepts admin WebSocket connections and fans out refresh events pushed by backend services.
- No auth enforced here; your admin site should open the socket only for logged-in admins.

Endpoints
- WS: /ws?adminId=ADMIN_ID
  - Query params:
    - adminId: required (string or number)
    - kind: optional ("admin" default; reserved "service")
- POST /api/push
  - Body: { "adminId": "123", "type": "refresh.telemetry", "payload": { ... } }
  - Returns: { delivered: N } or { delivered: 0, note: "no_active_clients" }
- GET /connections → { total, admin, service, rooms: [{ adminId, count }] }
- GET /health → { ok: true }

Environment (.env)
- PORT=8090
- MAX_WS_TOTAL=20000              # hard cap for all WebSockets
- MAX_WS_ADMIN=15000              # cap for admin WebSockets
- MAX_WS_PER_ADMIN=5              # cap per adminId
- MAX_WS_SERVICE=0                # reserved; not used today
- MAX_BACKPRESSURE=65536          # uWS backpressure bytes per socket
- PUSH_RPS=2000                   # simple rate limit for /api/push

Run
1) Copy .env.example to .env and adjust limits/port.
2) npm i
3) npm start

Wire services
- Set each service's admin.refresh-url to this service:
  - Example: admin.refresh-url: http://admin-socket:8090/api/push
- Payload shape is already compatible with services' AdminNotifier.

Notes
- Single process is fine initially (no PM2). If you need >1 CPU, scale with multiple processes behind a TCP LB; the current code keeps state in-process and does not yet cluster, which is OK for simple refresh use-cases.

