package app.gateway.config;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitListenerConfig {

    @Bean
    public SimpleRabbitListenerContainerFactory wsListenerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // High concurrency (tune prefetch if needed)
        factory.setConcurrentConsumers(50);
        factory.setMaxConcurrentConsumers(400);
        factory.setPrefetchCount(100);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
