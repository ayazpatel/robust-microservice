# Realtime WS (uWebSockets.js → RabbitMQ)

This service terminates Android WebSocket connections, verifies RS256 JWTs from the Auth service, normalizes JSON messages, and publishes them to RabbitMQ. It also lets you push a message to a specific device via a simple HTTP endpoint.

## JWT: what’s inside and how it’s enforced

Issued by Spring Auth (RS256), verified here using `AUTH_PUBLIC_PEM`.

Claims used:

- sub: appId
- appId: appId (redundant, convenience)
- deviceId: Android device’s unique id
- ip: optional; if present, the client’s IP must equal this at connect time
- iat, exp: standard issued-at and expiry

WebSocket upgrade rules:

- Token required via Sec-WebSocket-Protocol or Authorization: Bearer or `?token=` query.
- RS256-only verification with your public key.
- Validations:
	- deviceId is required.
	- If `jwt.ip` exists, it must equal the client IP (X-Forwarded-For first, then socket address).
- Redis-based uniqueness (per appId+deviceId) enforced post-open; duplicates are closed quickly.
- Auto-close a little after `exp` to force token refresh.

If the device’s IP changes:

- A connection attempt with an old JWT will fail if token `ip` differs from the real client IP.
- Client must fetch a new token with the new IP and reconnect.

## Message shapes and RabbitMQ envelope

Client → Server (preferred):

```json
{ "topic": "as_gateway.client.event", "data": { /* payload */ } }
```

Client → Server (minimal; no topic):

```json
{ "type": "something", "foo": "bar" }
```

Server publishes to RabbitMQ as an envelope:

```json
{
	"topic": "as_gateway.client.event",
	"data": { /* obj.data or the whole object if no data */ },
	"sender": {
		"kind": "android",
		"appId": "<appId>",
		"deviceId": "<deviceId>",
		"ip": "x.y.z.w" | null,
		"connId": "<connectionId>"
	},
	"ts": 1730000000000
}
```

Exchange and routing:

- Exchange: `RABBIT_EXCHANGE` (default `ws_ingress`)
- Routing key: message.topic if provided; otherwise `RABBIT_ROUTING_KEY` (default `client.event`)

Example client messages:

- Telemetry

```json
{ "topic": "as_gateway.telemetry", "data": { "deviceId": "<deviceId>", "battery": 82, "temp": 33.2 } }
```

- User action

```json
{ "topic": "as_gateway.user.action", "data": { "deviceId": "<deviceId>", "action": "login", "ts": 1730000000000 } }
```

- Minimal (defaults to routing key `client.event`)

```json
{ "type": "position", "lat": 18.5, "lng": 73.9 }
```

## Server → specific device (push)

Built-in HTTP helpers:

- List connections: `GET /connections` → `{ count, sample: [ { deviceId, appId, connId } ... ] }`
- Push to device: `POST /push/device` with body:

```json
{ "deviceId": "android-device-001", "payload": { "type": "server_msg", "text": "hello!" } }
```
Response is `{ delivered: true }` or `{ error: "not_connected" }`.

Use the REST Client file `tests/push.http` to try these quickly.

## Environment variables

From `config/env.local.example` and `config/env.baremetal.example`:

- PORT: WS port (default 443)
- AUTH_PUBLIC_PEM: RSA public key (single line with literal \n newlines)
- RABBIT_URL: amqp(s):// user:pass@host:5672/vhost
- RABBIT_EXCHANGE: topic exchange name (default `ws_ingress`)
- RABBIT_ROUTING_KEY: default routing key (default `client.event`)
- IDLE_TIMEOUT: seconds (default 120)
- MAX_PAYLOAD: bytes (default 1 MiB)
- REDIS_URL: optional redis://host:6379 (enables per-device uniqueness)
- SSL_KEY, SSL_CERT: enable TLS if set (port usually 443)
- WS_COMPRESSION: `none` or `shared` (optional)
- MAX_BACKPRESSURE, WS_WELCOME, WS_ACK: tuning options (optional)

## How to start everything

Option A — Local via Docker (RabbitMQ + Redis + WS):

1. Copy env and set public key

```powershell
Copy-Item config/env.local.example .env
# Paste your RSA public key in .env as a single line: AUTH_PUBLIC_PEM=-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----
```

1. Start services

```powershell
docker compose -f docker/docker-compose.local.yml up --build
```

1. RabbitMQ UI at <http://localhost:15672> (guest/guest)

Option B — Run WS locally with Bun (no Docker):

```powershell
Copy-Item config/env.local.example .env
# Set RABBIT_URL and AUTH_PUBLIC_PEM in .env
bun install
bun src/server.js
```

Option C — Bare-metal with PM2 (cluster):

```bash
# root shell
ulimit -n 1048576
sysctl -w net.core.somaxconn=65535
sysctl -w net.ipv4.ip_local_port_range="1024 65000"
sysctl -w net.ipv4.tcp_keepalive_time=45

# app user
cp config/env.baremetal.example .env && export $(cat .env | xargs)
npm ci && npm i -g pm2
pm2 start config/ecosystem.baremetal.config.js && pm2 status
```

Start Auth service (Spring Boot):

```powershell
# From repo root
cd auth
mvn spring-boot:run
# App listens on http://localhost:8081 (see rest/auth.http)
```

Mint a token and open WS:

1. Use `auth/rest/auth.http` → POST `/api/auth/token` with appId, apiKey, packageName, signatureSha, deviceId, ip, ttlSeconds.
2. Connect Android/Web client with the token:

`ws://localhost:8080?token=YOUR_JWT&deviceId=<deviceId>`

1. Send a structured message (topic + data) and confirm a RabbitMQ consumer receives the envelope.
1. From server, `POST /push/device` with the deviceId to validate server→client path.

Optional enhancements we can add:

- Rate limiting on `/push/device`
- Store-and-forward queue for offline devices
- Signed server→client envelopes for integrity auditing

## Notes

- Android clients should ping every 30–60s (e.g., OkHttp `pingInterval(45s)`).
- Server defaults to `idleTimeout=120s`; keep proxy/LB idle timeouts > 300s.
- JSON payloads today; can switch to CBOR/Protobuf later.
- Docker Compose on bare-metal is near-native performance; for absolute max, use PM2 directly.
