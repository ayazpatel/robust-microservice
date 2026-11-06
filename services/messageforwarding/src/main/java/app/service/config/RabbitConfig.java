package app.service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${app.queue}")
    private String queueName;

    @Value("${app.exchange:ws_ingress}")
    private String exchangeName;

    @Bean
    public Queue appQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public TopicExchange appExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Binding appBinding(Queue appQueue, TopicExchange appExchange) {
        // Bind to both batch and any future variants under messageforwarding.*
        // Primary binding for batch topic
        return BindingBuilder.bind(appQueue).to(appExchange).with("service.messageforwarding.*");
    }
}
