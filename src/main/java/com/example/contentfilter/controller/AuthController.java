package com.example.contentfilter.controller;

import com.example.contentfilter.account.UserRole;
import com.example.contentfilter.security.AuthenticationService;
import com.example.contentfilter.security.AuthenticationService.LoginResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("v2")
@RequestMapping("/api/v2/auth")
public class AuthController {

	private final AuthenticationService authenticationService;

	public AuthController(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	/** Exchanges credentials for a bearer token. The response must never be cached. */
	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResult result = authenticationService.login(request.email(), request.password());
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(new TokenResponse(result.accessToken(), "Bearer", result.expiresAt(),
						result.userId(), result.email(), result.role()));
	}

	/** Who the presented token belongs to; handy for a frontend after reload. */
	@GetMapping("/me")
	public CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return new CurrentUserResponse(
				UUID.fromString(jwt.getSubject()),
				UserRole.valueOf(jwt.getClaimAsString("role")),
				jwt.getExpiresAt());
	}

	public record LoginRequest(
			@NotBlank @Size(max = 320) String email,
			@NotBlank @Size(max = 200) String password) {

		@Override
		public String toString() {
			return "LoginRequest[email=" + email + ", password=<redacted>]";
		}
	}

	public record TokenResponse(
			String accessToken,
			String tokenType,
			Instant expiresAt,
			UUID userId,
			String email,
			UserRole role) {
	}

	public record CurrentUserResponse(UUID userId, UserRole role, Instant expiresAt) {
	}
}
