# Call Forwarding Service – gRPC API

This service exposes a gRPC API for internal calls (admin-gateway -> service) while keeping the existing REST API for external clients.

## What’s included

- Protobuf schema at `src/main/proto/callforwarding.proto` with:
  - `ListCallForwardRules` RPC
  - `ListCallForwardRequest` filters (deviceId, subscriptionId, reason, service, page/size)
  - `CallForwardRule` message (fields mirror the REST JSON)
- Netty-based gRPC server started by `GrpcServerConfig` on `grpc.port` (default 9193)
- Service implementation `CallForwardingGrpcService` backed by the Redis store

## Run / Build

- Build with protobuf codegen:

```powershell
# Windows PowerShell
mvnw -Pwith-proto -DskipTests package
```

- Configure port in `application.yml`:

```yaml
grpc:
  port: 9193
```

## Notes

- For simplicity the proto lives with the service. In a multi-service setup, prefer a shared `proto` module to avoid duplication.
- The gRPC API mirrors the REST response shape (items/page/size/count) for consistency.
