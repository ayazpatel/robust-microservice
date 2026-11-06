package app.gateway.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceTopologyConfig {
    @Value("${app.service.exchange:service.events}")
    private String serviceExchange;

    @Bean
    public TopicExchange serviceEventsExchange() {
        return new TopicExchange(serviceExchange, true, false);
    }
}
