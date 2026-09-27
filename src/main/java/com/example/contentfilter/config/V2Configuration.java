package com.example.contentfilter.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Shared infrastructure for the V2 review modules. */
@Configuration(proxyBeanMethods = false)
@Profile("v2")
public class V2Configuration {

	/** Injected rather than calling Instant.now(), so tests can control time. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
