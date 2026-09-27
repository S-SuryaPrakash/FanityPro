package com.example.contentfilter.security;

import com.example.contentfilter.account.AppUser;
import com.example.contentfilter.account.AppUserRepository;
import com.example.contentfilter.account.UserRole;
import com.example.contentfilter.audit.AuditAction;
import com.example.contentfilter.audit.AuditService;
import com.example.contentfilter.exception.InvalidCredentialsException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exchanges email and password for a short-lived access token. */
@Service
@Profile("v2")
public class AuthenticationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

	private final AuthenticationManager authenticationManager;
	private final AccessTokenService accessTokenService;
	private final AppUserRepository users;
	private final AuditService audit;
	private final Clock clock;

	AuthenticationService(
			AuthenticationManager authenticationManager,
			AccessTokenService accessTokenService,
			AppUserRepository users,
			AuditService audit,
			Clock clock) {
		this.authenticationManager = authenticationManager;
		this.accessTokenService = accessTokenService;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public LoginResult login(String email, String password) {
		Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(
							AppUser.normalizeEmail(email), password));
		} catch (AuthenticationException exception) {
			// No email in the log: failed attempts often contain typos of real addresses.
			LOGGER.info("Login rejected: {}", exception.getClass().getSimpleName());
			throw new InvalidCredentialsException();
		}

		AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
		Instant now = clock.instant();
		users.findById(principal.id()).ifPresent(user -> user.recordLogin(now));
		audit.record(principal.id(), AuditAction.LOGIN_SUCCEEDED, "USER", principal.id(), Map.of());

		AccessTokenService.IssuedToken token = accessTokenService.issue(principal);
		return new LoginResult(
				token.value(), token.expiresAt(), principal.id(), principal.email(), principal.role());
	}

	public record LoginResult(
			String accessToken,
			Instant expiresAt,
			UUID userId,
			String email,
			UserRole role) {
	}
}
