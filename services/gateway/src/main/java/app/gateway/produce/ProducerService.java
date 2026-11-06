package app.gateway.produce;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProducerService {
    private static final Logger log = LoggerFactory.getLogger(ProducerService.class);

    private final RabbitTemplate rabbit;
    private final AmqpAdmin admin;
    private static final String SERVICE_PREFIX = "service.";

    @Value("${app.service.exchange:service.events}")
    private String serviceExchange;

    private final Map<String, Boolean> declared = new ConcurrentHashMap<>();

    // Optional cap to avoid broker overload; if null, we won't set x-max-length to prevent
    // PRECONDITION_FAILED on queues that already exist without this arg.
    @Value("${app.service.queue.maxLength:#{null}}")
    private Integer maxQueueLength;

    public ProducerService(RabbitTemplate rabbit, AmqpAdmin admin) {
        this.rabbit = rabbit;
        if (admin instanceof RabbitAdmin ra) {
            this.admin = ra;
        } else {
            this.admin = new RabbitAdmin(rabbit.getConnectionFactory());
        }
    }

    /**
     * Route a WS envelope to a service queue by topic.
     * Expected topic format: service.<serviceName>.<rest>
     * Example: service.telemetry.device.update → queue: service.telemetry.q (bind pattern service.telemetry.#)
     */
    public void routeByTopic(JsonNode envelope) {
        if (envelope == null) return;
        String topic = envelope.path("topic").asText("");
        if (!topic.startsWith(SERVICE_PREFIX)) {
            // only handle service.* here
            return;
        }

        String[] parts = topic.split("\\.", 3);
        if (parts.length < 2) return;
        String service = parts[1];
        String routingKey = parts.length >= 3 ? (SERVICE_PREFIX + parts[1] + "." + parts[2]) : topic;

        String queueName = SERVICE_PREFIX + service + ".q";      // e.g., service.telemetry.q
        String bindPattern = SERVICE_PREFIX + service + ".#";     // e.g., service.telemetry.#

        ensureQueueBound(queueName, bindPattern);
        publish(routingKey, envelope);
    }

    private void ensureQueueBound(String queueName, String bindPattern) {
        if (declared.putIfAbsent(queueName, Boolean.TRUE) != null) return;

        // Ensure exchange exists (idempotent declare)
        TopicExchange ex = new TopicExchange(serviceExchange, true, false);
        admin.declareExchange(ex);

        // If the queue already exists (possibly declared elsewhere with different args),
        // do NOT redeclare with args to avoid PRECONDITION_FAILED; just bind it.
        java.util.Properties existing = admin.getQueueProperties(queueName);
        Queue q;
        if (existing == null) {
            java.util.Map<String, Object> args = new java.util.HashMap<>();
            args.put("x-queue-mode", "lazy"); // store on disk
            if (maxQueueLength != null && maxQueueLength > 0) {
                args.put("x-max-length", maxQueueLength);
            }
            q = new Queue(queueName, true, false, false, args);
            admin.declareQueue(q);
            log.info("[SERVICE_TOPOLOGY] declared queue={} args={} (new)", queueName, args);
        } else {
            // Passive reference to existing durable queue
            q = new Queue(queueName, true);
            log.info("[SERVICE_TOPOLOGY] using existing queue={} properties={}", queueName, existing);
        }

        Binding b = BindingBuilder.bind(q).to(ex).with(bindPattern);
        admin.declareBinding(b);
        log.info("[SERVICE_TOPOLOGY] bound queue={} to exchange={} pattern={}", queueName, serviceExchange, bindPattern);
    }

    private void publish(String routingKey, Object payload) {
        rabbit.convertAndSend(serviceExchange, routingKey, payload, m -> {
            m.getMessageProperties().setContentType("application/json");
            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return m;
        });
        log.debug("[SERVICE_PUBLISH] exchange={} rk={}", serviceExchange, routingKey);
    }
}
