# ws-server (Windows)

This directory contains CMake presets and scripts to build and run the C++ WebSocket server on Windows with vcpkg.

## Prerequisites
- Visual Studio 2022 (C++ workload) or Build Tools
- Visual Studio Developer PowerShell (use this shell to ensure cl.exe is on PATH)
- vcpkg is vendored at `../../vcpkg` and used via CMake toolchain
- Redis and RabbitMQ available locally or remotely

## Configure & build
From this directory:

```powershell
cmake --preset ninja-vcpkg-debug --fresh
cmake --build --preset ninja-vcpkg-debug
```

The binary will be at `out/build/ninja-vcpkg-debug/ws_server.exe`.

## Quick run via script
An easy way is to copy `.env.example` to `.env`, update values, then run:

```powershell
# From ws-server/windows
scripts/run.ps1
```

Options:
- `-Preset <name>` to pick another CMake preset (default: ninja-vcpkg-debug)
- `-Fresh` to force a clean configure before building
- `-EnvFile <path>` to specify a different .env file

## Configuration (.env)
Important variables:

- PORT: HTTP/WS port (default 8080)
- AUTH_PUBLIC_PEM_FILE | AUTH_PUBLIC_PEM_B64 | AUTH_PUBLIC_PEM: JWT verification key (choose one)
- AUTH_DEV_MODE: `true` to bypass JWT checks for local testing
- RABBIT_URL: e.g. `amqp://user:pass@host:5672/vhost`
- RABBIT_EXCHANGE: exchange to publish to (default `ws_ingress`)
- RABBIT_ROUTING_KEY: routing key for published messages
- REDIS_URL: e.g. `tcp://127.0.0.1:6379`
- WS_WELCOME, WS_ACK, DEBUG_WS: feature flags for behavior/logging
- IDLE_TIMEOUT, MAX_PAYLOAD, MAX_BACKPRESSURE, WS_COMPRESSION: performance knobs

## Endpoints
- `GET /health` → `ok`
- `GET /connections` → JSON snapshot of local/redis connections
- `GET /debug/redis` → JSON status of redis registry
- `GET /debug/conn/:id` → info for one device id
- `POST /push/device` → `{ deviceId, payload }` enqueues/pushes to device

## Notes
- AMQP over TLS (amqps://) isn’t wired yet. Current build uses plain AMQP. If you need TLS, we can add rabbitmq-c SSL socket initialization.
- If Redis isn’t configured, push and registry features gracefully no-op, and only local in-memory tracking is used.

## Troubleshooting
- Use the Developer PowerShell to avoid missing compiler/headers.
- If packages are being built for a long time, it’s vcpkg fetching/building dependencies the first time.
- Reconfigure cleanly if CMake cache gets stale:

```powershell
cmake --preset ninja-vcpkg-debug --fresh
```

- To see env vars in use, the run script logs PORT/RABBIT_URL/REDIS_URL on startup.