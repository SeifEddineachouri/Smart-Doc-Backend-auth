package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class SmartdocApplication {

	private static final Logger LOGGER = LoggerFactory.getLogger(SmartdocApplication.class);

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(SmartdocApplication.class, args);
		logStartupInfo(context);
	}

	private static void logStartupInfo(ConfigurableApplicationContext context) {
		Environment environment = context.getEnvironment();
		String appName = environment.getProperty("spring.application.name", "smartdoc");
		String[] activeProfiles = environment.getActiveProfiles();
		String profiles = activeProfiles.length == 0 ? "default" : String.join(", ", activeProfiles);

		int port = context instanceof WebServerApplicationContext webServerContext
			? webServerContext.getWebServer().getPort()
			: Integer.parseInt(environment.getProperty("server.port", "8080"));

		String host = environment.getProperty("app.host", "localhost");
		String baseUrl = "http://" + host + ":" + port;
		String datasourceUrl = environment.getProperty("spring.datasource.url", "unknown");

		LOGGER.info("""
			=====================================================
			  {} is up and running.
			  Active profile(s): {}
			  API base URL      : {}
			  Swagger UI        : {}/swagger-ui/index.html
			  Datasource        : {}
			=====================================================
			""", appName, profiles, baseUrl, baseUrl, datasourceUrl);
	}

}
