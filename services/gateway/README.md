# Gateway Messaging Guide

This gateway consumes all WebSocket ingress events and can re-publish (produce) messages for your actual services to consume. This document explains:

- How the WebSocket → RabbitMQ → Gateway → Services flow works
- How to produce messages from the gateway to specific exchanges/queues
- How services consume those messages with high concurrency
- How many queues you can/should create and how to design your topology

## Architecture

- WebSocket service publishes JSON envelopes to a topic exchange (default: `ws_ingress`).
- Gateway consumes all topics (routing `#`) from a durable queue bound to `ws_ingress`.
- Gateway enriches/reroutes and produces to service-facing exchanges (e.g., `service.events`).
- Each service gets its own durable queue(s) bound by routing keys relevant to that service.

```
Android/Web → [WebSocket] → (topic: ws_ingress) → [Gateway] → (topic: service.events) → [ServiceA queue]
                                                                ↳ (topic: service.audit)  → [ServiceB queue]
```

Default queue/exchange in this repo (you can rename in properties):

- Ingress exchange: `ws_ingress` (topic)
- Gateway consumer queue: `gateway.ws_ingress` (durable)

## Payload shape (envelope)

WebSocket publishes envelopes like:

```json
{
  "topic": "as_gateway.client.event",
  "data": { /* client payload */ },
  "sender": {
    "kind": "android",
    "appId": "<appId>",
    "deviceId": "<deviceId>",
    "ip": "x.y.z.w",
    "connId": "<connectionId>"
  },
  "ts": 1730000000000
}
```

## Consuming all WebSocket requests (Gateway)

The gateway already contains a high‑concurrency listener that consumes from `gateway.ws_ingress` and logs each message.

- Listener: `app.gateway.ingress.WsIngressListener`
- Container factory: `wsListenerFactory` with `maxConcurrentConsumers=400` (starts at 50, prefetch=100)
- Binding: `gateway.ws_ingress` queue → `ws_ingress` exchange with routing `#`

Properties (services/gateway/src/main/resources/application.properties):

```
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

app.rabbit.exchange=ws_ingress
app.rabbit.queue=gateway.ws_ingress
app.rabbit.routing=#
```

Run it:

- Windows PowerShell
  - cd services/gateway
  - mvnw.cmd spring-boot:run
- Linux/macOS
  - cd services/gateway
  - ./mvnw spring-boot:run

You should see logs like:

```
[WS_INGRESS] rk=as_gateway.client.event ct=application/json json={...}
```

## Producing messages to service exchanges

You’ll typically produce to a service-facing topic exchange (e.g., `service.events`) so multiple services can bind with different routing keys.

### Minimal producer with RabbitTemplate

```java
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {
  private final RabbitTemplate rabbit;
  public ProducerService(RabbitTemplate rabbit) { this.rabbit = rabbit; }

  public void publishToServices(String exchange, String routingKey, Object payload) {
    rabbit.convertAndSend(exchange, routingKey, payload, m -> {
      m.getMessageProperties().setContentType("application/json");
      m.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
      return m;
    });
  }
}
```

Usage inside the gateway listener:

```java
// In WsIngressListener after parsing the incoming envelope
producerService.publishToServices("service.events", "telemetry.device", envelopeNode);
```

### Declaring service exchanges and queues

Use Spring beans to declare exchanges, queues, and bindings so they’re created on startup.

```java
@Configuration
public class ServiceTopologyConfig {
  @Bean TopicExchange serviceEvents() { return new TopicExchange("service.events", true, false); }
  @Bean Queue telemetryQ() { return new Queue("service.telemetry.q", true); }
  @Bean Binding telemetryBinding(Queue telemetryQ, TopicExchange serviceEvents) {
    return BindingBuilder.bind(telemetryQ).to(serviceEvents).with("telemetry.#");
  }

  @Bean Queue auditQ() { return new Queue("service.audit.q", true); }
  @Bean Binding auditBinding(Queue auditQ, TopicExchange serviceEvents) {
    return BindingBuilder.bind(auditQ).to(serviceEvents).with("audit.#");
  }
}
```

### Consuming in services

In a service project, add:

```java
@RabbitListener(queues = "service.telemetry.q", concurrency = "10-200")
public void handleTelemetry(String json) {
  // parse JSON and work
}
```

Or configure a factory with prefetch and max concurrency like we did in the gateway.

## How many queues can we create?

- There’s no fixed hard limit in RabbitMQ for the number of queues; limits are practical and resource-based (memory, CPU, file descriptors, disk I/O).
- Rules of thumb:
  - Thousands of durable queues per node are common when sized appropriately.
  - Tens of thousands are possible but require tuning and careful monitoring.
  - Hundreds of thousands or millions are not recommended.
- Each queue has overhead (RAM for metadata, consumers, bindings, internal structures). Plan capacity and monitor.
- Prefer fewer queues with topic routing instead of one-queue-per-entity when possible.
- Queue names can be up to 255 bytes.

## Topology patterns

- Topic fan‑out (recommended):
  - Exchange: `service.events` (topic)
  - Routing examples: `telemetry.device`, `audit.login`, `user.signup`
  - Queues bind with patterns: `telemetry.#`, `audit.#`
- Direct routing:
  - Exchange: `service.direct` (direct)
  - Queues bind exact keys (e.g., `alerts.high`)
- Dead‑lettering and retry:
  - Add DLX (`service.dlx`) with DLQ per consumer; use `x-dead-letter-exchange` policy.
  - Use TTL with `x-message-ttl` for delayed retries or a dedicated delay plugin.

## Recommended settings

- Make all exchanges and queues durable; publish persistent messages (deliveryMode=2).
- Set consumer prefetch (e.g., 50–300) based on message size and processing time.
- Use concurrency ranges (e.g., `10-200`) per listener or a container factory with `maxConcurrentConsumers`.
- Keep routing keys consistent and documented.

## End‑to‑end example

1. Gateway consumes from `ws_ingress` → receives envelope `{ topic, data, sender, ts }`.
2. Gateway maps the envelope to a domain route, e.g., `telemetry.device` on `service.events`.
3. Telemetry service binds `service.telemetry.q` to `service.events` with `telemetry.#` and consumes at high concurrency.
4. Audit service binds `service.audit.q` to `service.events` with `audit.#` and logs to SIEM.

## Running everything locally

- RabbitMQ: `docker compose -f websocket/docker/docker-compose.local.yml up -d rabbitmq`
- WebSocket server: run per websocket/README.md and connect a WS client.
- Gateway:
  - Windows: `mvnw.cmd spring-boot:run`
  - Linux/macOS: `./mvnw spring-boot:run`
- Create a service project (Spring Boot + spring-boot-starter-amqp), declare a queue/binding, and add a `@RabbitListener`.

## Troubleshooting

- No messages in service queue? Check bindings to the exchange and routing keys.
- High CPU/low throughput? Tune `prefetch`, concurrency, and ensure message size is reasonable.
- Consumer drops or duplicates? Consider idempotency and retries with DLQs; avoid auto-requeue when handlers throw.
