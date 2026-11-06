# Admin Gateway – gRPC Clients

The admin-gateway now uses gRPC for internal calls to services. HTTP endpoints remain the same for external clients.

## What’s included

- Protobuf schema for callforwarding at `src/main/proto/callforwarding.proto`
- Simple gRPC client in `com.admingateway.grpc.CallForwardingGrpcClient`
- Controller `CallForwardingRestProxyController` maps HTTP -> gRPC and returns JSON

## Configure

In `src/main/resources/application.yml`:

```yaml
gateway:
  callforwarding-grpc:
    host: localhost
    port: 9193
```

## Build

```powershell
# Windows PowerShell
mvnw -Pwith-proto -DskipTests package
```

## Next steps

- Extract all service protos to a shared `proto` module to avoid duplication
- Repeat this pattern for telemetry, messages, calllogs, and contacts
- Optionally enable TLS on gRPC channels and add auth metadata
