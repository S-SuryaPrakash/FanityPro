package com.example.contentfilter.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * V2 authentication settings. Bound in every profile but only validated by
 * {@link #requireValidForV2()}, so V1-only deployments need no JWT secret.
 *
 * @param jwt access-token signing settings
 * @param bootstrapAdmin first administrator, created only when no account exists
 */
@ConfigurationProperties("content-filter.security")
public record SecurityProperties(
		@DefaultValue Jwt jwt,
		@DefaultValue BootstrapAdmin bootstrapAdmin) {

	/** HS256 requires a key of at least 256 bits. */
	static final int MIN_SECRET_BYTES = 32;

	/** Fails startup with a clear message instead of issuing weakly signed tokens. */
	void requireValidForV2() {
		if (jwt.secret() == null
				|| jwt.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"content-filter.security.jwt.secret (CONTENT_FILTER_JWT_SECRET) must be set "
							+ "to at least " + MIN_SECRET_BYTES + " bytes when the v2 profile is active.");
		}
		if (jwt.accessTokenTtl().isNegative() || jwt.accessTokenTtl().isZero()) {
			throw new IllegalStateException("content-filter.security.jwt.access-token-ttl must be positive.");
		}
	}

	public record Jwt(
			String secret,
			@DefaultValue("contentfilter") String issuer,
			@DefaultValue("30m") Duration accessTokenTtl) {
	}

	public record BootstrapAdmin(String email, String password) {

		boolean isConfigured() {
			return email != null && !email.isBlank() && password != null && !password.isBlank();
		}
	}
}
