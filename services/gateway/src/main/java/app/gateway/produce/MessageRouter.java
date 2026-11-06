package app.gateway.produce;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class MessageRouter {

    /**
     * Determine routing key for downstream service exchange.
     * Priority:
     * 1) If payload has field "type" starting with "service.", use it directly (e.g., service.telemetry)
     * 2) Else if envelope has "topic" = as_gateway.X.Y, map to service.X.Y
     * 3) Fallback to "service.client.event"
     */
    public String route(JsonNode envelope) {
        if (envelope == null) return "service.client.event";
        // Prefer explicit type in data
        JsonNode data = envelope.get("data");
        if (data != null && data.has("type")) {
            String t = data.get("type").asText("");
            if (t.startsWith("service.")) return t;
        }
        // Fallback: map topic
        String topic = envelope.path("topic").asText("");
        if (!topic.isEmpty()) {
            if (topic.startsWith("as_gateway.")) {
                return "service." + topic.substring("as_gateway.".length());
            }
            // If client already sent service.* use as-is
            if (topic.startsWith("service.")) return topic;
        }
        return "service.client.event";
    }
}
