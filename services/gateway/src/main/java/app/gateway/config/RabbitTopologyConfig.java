package app.gateway.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Value("${app.rabbit.exchange:ws_ingress}")
    private String exchangeName;

    @Value("${app.rabbit.queue:gateway.ws_ingress}")
    private String queueName;

    @Value("${app.rabbit.routing:#}")
    private String routingPattern;

    @Bean
    public TopicExchange wsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue wsQueue() {
        // Durable, non-exclusive, non-auto-delete queue
        return new Queue(queueName, true, false, false);
    }

    @Bean
    public Binding wsBinding(Queue wsQueue, TopicExchange wsExchange) {
        return BindingBuilder.bind(wsQueue).to(wsExchange).with(routingPattern);
    }
}
