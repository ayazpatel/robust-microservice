package app.gateway.ingress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.gateway.produce.ProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WsIngressListener {

    private static final Logger log = LoggerFactory.getLogger(WsIngressListener.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProducerService producerService;

    public WsIngressListener(ProducerService producerService) {
        this.producerService = producerService;
    }

    @RabbitListener(queues = "${app.rabbit.queue:gateway.ws_ingress}", containerFactory = "wsListenerFactory")
    public void onMessage(Message message) {
        try {
            MessageProperties props = message.getMessageProperties();
            String routingKey = props.getReceivedRoutingKey();
            String contentType = props.getContentType();
            byte[] body = message.getBody();

            String raw = new String(body);
            JsonNode json;
            try {
                json = mapper.readTree(raw);
            } catch (Exception parseEx) {
                json = null; // Not JSON; log raw
            }

            if (json != null) {
                log.info("[WS_INGRESS] rk={} ct={} json={} ", routingKey, contentType, json.toString());
                // Route service.* topics to service exchange/queues
                producerService.routeByTopic(json);
            } else {
                log.info("[WS_INGRESS] rk={} ct={} raw={} ", routingKey, contentType, raw);
            }
        } catch (Exception e) {
            log.error("[WS_INGRESS] listener error", e);
        }
    }
}
