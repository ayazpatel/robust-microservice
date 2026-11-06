package app.service.consumer;

import app.service.dto.EventMessage;
import app.service.service.CallForwardProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(QueueConsumer.class);

    private final ObjectMapper om;
    private final CallForwardProcessor processor;

    public QueueConsumer(ObjectMapper om, CallForwardProcessor processor) {
        this.om = om;
        this.processor = processor;
    }

    @Value("${app.queue}")
    private String queue;

    @RabbitListener(queues = "${app.queue}")
    public void onMessage(Message message) {
        try {
            byte[] body = message.getBody();
            if (body == null || body.length == 0) {
                log.warn("Empty message on {}", queue);
                return;
            }

            // Detect legacy Java serialization stream: 0xAC 0xED 0x00 0x05 ...
            if ((body[0] == (byte)0xAC) && (body[1] == (byte)0xED)) {
                log.warn("Dropping legacy Java-serialized message on {} (magic AC ED)", queue);
                return; // ack & drop; produced before JSON converter was enabled
            }

            EventMessage msg = om.readValue(body, EventMessage.class);
            // offload to async pool; listener threads stay free
            processor.process(msg);
        } catch (Exception e) {
            log.error("Invalid message on {}: {}", queue, e.getMessage());
            // message will be acked by default; rely on idempotency/alerts
        }
    }
}
