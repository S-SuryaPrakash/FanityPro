package com.example.contentfilter.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived HS256 access tokens. The subject is the account ID and
 * the {@value #ROLE_CLAIM} claim carries the single role; no refresh tokens.
 */
@Service
@Profile("v2")
public class AccessTokenService {

	static final String ROLE_CLAIM = "role";

	private final JwtEncoder encoder;
	private final SecurityProperties.Jwt settings;
	private final Clock clock;

	AccessTokenService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
		this.encoder = encoder;
		this.settings = properties.jwt();
		this.clock = clock;
	}

	public IssuedToken issue(AppUserPrincipal principal) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(settings.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(settings.issuer())
				.subject(principal.id().toString())
				.issuedAt(now)
				.expiresAt(expiresAt)
				.id(UUID.randomUUID().toString())
				.claim(ROLE_CLAIM, principal.role().name())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	public record IssuedToken(String value, Instant expiresAt) {
	}
}
