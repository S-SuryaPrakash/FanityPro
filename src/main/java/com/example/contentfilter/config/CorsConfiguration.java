package com.example.contentfilter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Relaxed CORS policy for local development only.
 *
 * <p>Disabled by default. Set {@code content-filter.cors.allowed-origins}
 * to enable (e.g. {@code http://localhost:5173} for the Vite dev server).
 * In production, the frontend is served from the same origin so CORS is
 * unnecessary.</p>
 */
@Configuration
@ConditionalOnProperty("content-filter.cors.allowed-origins")
public class CorsConfiguration implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedMethods("POST")
				.allowedHeaders("Content-Type")
				.maxAge(3600);
	}
}
