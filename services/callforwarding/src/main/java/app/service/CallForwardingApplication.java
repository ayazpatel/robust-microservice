package app.service;

import app.service.config.AdminProps;
import app.service.config.AppProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties({AppProps.class, AdminProps.class})
public class CallForwardingApplication {

	public static void main(String[] args) {
		SpringApplication.run(CallForwardingApplication.class, args);
	}

}
