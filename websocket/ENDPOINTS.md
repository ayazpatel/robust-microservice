# ENDPOINTS.md

This document lists all REST and WebSocket endpoints exposed by the Realtime WS (uWebSockets.js → RabbitMQ) service.

## WebSocket Endpoint

### `ws://<host>:<PORT>`
- **Upgrade requirements:**
  - JWT token via one of:
    - `Sec-WebSocket-Protocol` header
    - `Authorization: Bearer <token>` header
    - `?token=<JWT>` query parameter
  - `deviceId` required (from JWT; query param ignored for auth)
- **Auth:** RS256 JWT, verified with `AUTH_PUBLIC_PEM`.
- **Enforced:**
  - If JWT contains `ip`, client IP must match.
  - Only one active connection per device (Redis lock). A newer connection for the same device will kick the old one ("takeover").
  - Auto-close after JWT expiry.

#### Connection IP resolution
- If JWT includes `ip`, it must match the client’s real IP and is used as the connection IP.
- Otherwise, the server uses the client IP (X-Forwarded-For first, then socket address).
- The selected IP is exposed as `ws.ip` and included in the published envelope `sender.ip`.

#### Message Shapes
- **Client → Server:**
  - `{ "topic": "as_gateway.client.event", "data": { /* payload */ } }`
  - `{ "type": "something", "foo": "bar" }` (minimal)
- **Server → RabbitMQ:**
  - Envelope: `{ topic, data, sender: { kind, appId, deviceId, ip, connId }, ts }`
    - Note: `sender.ip` follows the IP resolution rules above.
- **Server → Client:**
  - On push: `{ type: "server_msg", ... }`
  - On error: `{ type: "error", error }`
  - On ping: `{ type: "pong", t }`
  - On ack (if enabled): `{ type: "ack", id }`

## REST Endpoints

### Health
- `GET /health`
  - Returns `ok` if server is running.

### Connections
- `GET /connections`
  - Returns `{ count, sample: [ { deviceId, appId, connId, online } ... ] }`

### Push to Device
- `POST /push/device`
  - Body: `{ deviceId: "android-device-001", payload: { ... } }`
  - Response: `{ delivered: true }` or `{ error: "not_connected" }`
  - If Redis enabled: `{ accepted: true, online }`

### Debug Endpoints
- `GET /debug/redis`
  - Returns Redis registry status: `{ enabled, count, sample }`
- `GET /debug/conn/:id`
  - Returns device registry info: `{ id, online, info }`

## Environment Variables

See `config/env.local.example` and `config/env.baremetal.example` for all supported variables.

- `PORT`: WS/HTTP port
- `AUTH_PUBLIC_PEM`, `AUTH_PUBLIC_PEM_FILE`, `AUTH_PUBLIC_PEM_B64`: RSA public key
- `RABBIT_URL`: AMQP URL
- `RABBIT_EXCHANGE`: RabbitMQ exchange
- `RABBIT_ROUTING_KEY`: Default routing key
- `IDLE_TIMEOUT`: WS idle timeout
- `MAX_PAYLOAD`: Max payload size
- `REDIS_URL`: Redis connection URL
- `SSL_KEY`, `SSL_CERT`: TLS cert/key
- `WS_COMPRESSION`: none/shared
- `MAX_BACKPRESSURE`: WS backpressure
- `WS_WELCOME`: Send welcome message
- `WS_ACK`: Send ACKs
- `DEBUG_WS`: Enable debug logs
- `AUTH_DEV_MODE`: Bypass JWT for dev

## Notes
- All endpoints require correct environment configuration.
- WebSocket connections are subject to JWT, deviceId, and IP enforcement.
- Redis and RabbitMQ must be running for full functionality.
