package app.service.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${app.queue}")
    private String queueName;

    @Bean
    public Queue appQueue() {
        // durable queue to survive broker restarts
        return new Queue(queueName, true);
    }

    @Bean
    public TopicExchange serviceEventsExchange() {
        // aligns with gateway producer exchange name
        return new TopicExchange("service.events", true, false);
    }

    @Bean
    public Binding cardsBinding(Queue appQueue, TopicExchange serviceEventsExchange) {
        // bind the cards queue to service.events exchange with routing key equal to the queue name
        return BindingBuilder.bind(appQueue).to(serviceEventsExchange).with(queueName);
    }
}
