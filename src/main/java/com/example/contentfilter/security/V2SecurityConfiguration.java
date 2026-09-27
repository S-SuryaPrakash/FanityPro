package com.example.contentfilter.security;

import com.example.contentfilter.account.AppUser;
import com.example.contentfilter.account.AppUserRepository;
import com.example.contentfilter.account.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless V2 security: {@code POST /api/v2/auth/login} issues an HS256 JWT,
 * and every other {@code /api/v2} call must present it as a bearer token.
 *
 * <p>V1 endpoints stay unauthenticated so existing clients keep working;
 * see the V2 notes in PROJECT_STATUS.md before exposing V1 publicly.</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("v2")
public class V2SecurityConfiguration {

	private static final String ADMIN = UserRole.ADMIN.name();
	private static final String ANALYST = UserRole.ANALYST.name();

	@Bean
	SecurityFilterChain v2SecurityFilterChain(
			HttpSecurity http,
			JwtAuthenticationConverter jwtAuthenticationConverter,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
			throws Exception {
		ProblemDetailSecurityHandler problemHandler =
				new ProblemDetailSecurityHandler(exceptionResolver);
		http
				// Bearer tokens are not sent automatically by browsers, so CSRF does not apply.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session ->
						session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/actuator/health/**", "/actuator/info",
								"/actuator/prometheus", "/error").permitAll()
						.requestMatchers("/api/v1/**", "/upload").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v2/auth/login").permitAll()
						.requestMatchers("/api/v2/users/**", "/api/v2/audit-events/**").hasRole(ADMIN)
						.requestMatchers("/api/v2/**").hasAnyRole(ADMIN, ANALYST)
						.anyRequest().denyAll())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
						.authenticationEntryPoint(problemHandler)
						.accessDeniedHandler(problemHandler))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(problemHandler)
						.accessDeniedHandler(problemHandler));
		return http.build();
	}

	@Bean
	SecretKey jwtSigningKey(SecurityProperties properties) {
		properties.requireValidForV2();
		return new SecretKeySpec(
				properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	/** Verifies signature, expiry, and issuer; rejects tokens signed with any other algorithm. */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
		return decoder;
	}

	/** Maps the single {@code role} claim to a {@code ROLE_*} authority. */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(AccessTokenService.ROLE_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	/** BCrypt by default, with an algorithm prefix so hashes can be upgraded later. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(AppUserRepository users) {
		return email -> users.findByEmail(AppUser.normalizeEmail(email))
				.map(AppUserPrincipal::from)
				.orElseThrow(() -> new UsernameNotFoundException("Unknown account."));
	}

	@Bean
	AuthenticationManager authenticationManager(
			UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}
}
