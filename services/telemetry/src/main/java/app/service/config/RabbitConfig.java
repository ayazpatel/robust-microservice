package app.service.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${app.queue}")
    private String queueName;

    @Bean
    public Queue appQueue() {
        // durable queue; change to false if you want ephemeral
        return new Queue(queueName, true);
    }
}