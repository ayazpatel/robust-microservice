package app.service;

import app.service.config.AdminProps;
import app.service.config.AppProps;
import app.service.config.GrpcProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties({AppProps.class, AdminProps.class, GrpcProps.class})
public class CallLogsApplication {

	public static void main(String[] args) {
		SpringApplication.run(CallLogsApplication.class, args);
	}

}
