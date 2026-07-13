package com.example.contentfilter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Application entry point for the content-filtering API.
 *
 * <p>Bootstraps Spring Boot and scans the project packages for controllers,
 * services, and exception handlers.</p>
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.example.contentfilter")
public class ContentfilterApplication {

	/**
	 * Starts the embedded web server and initializes the Spring application context.
	 *
	 * @param args command-line arguments passed to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(ContentfilterApplication.class, args);
	}

}
