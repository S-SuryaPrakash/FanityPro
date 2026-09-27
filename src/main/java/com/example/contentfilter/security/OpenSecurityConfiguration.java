package com.example.contentfilter.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Without the v2 profile there are no accounts, so the V1 API stays open exactly
 * as it was before Spring Security was added. Security headers still apply.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!v2")
public class OpenSecurityConfiguration {

	@Bean
	SecurityFilterChain openSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				// V1 is a stateless multipart API with no session or cookie to protect.
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
		return http.build();
	}
}
