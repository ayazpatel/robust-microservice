package app.service.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public Queue netbankingQueue(@Value("${app.queue}") String q) {
        // Declare the queue if it doesn't exist so the listener doesn't fail on startup
        return new Queue(q, true, false, false);
    }
}
