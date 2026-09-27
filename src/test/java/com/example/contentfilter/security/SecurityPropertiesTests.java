package com.example.contentfilter.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTests {

	private static final SecurityProperties.BootstrapAdmin NO_ADMIN =
			new SecurityProperties.BootstrapAdmin(null, null);

	@Test
	void v2RequiresASigningSecretOfAtLeast256Bits() {
		assertThrows(IllegalStateException.class, () -> properties(null).requireValidForV2());
		assertThrows(IllegalStateException.class,
				() -> properties("x".repeat(SecurityProperties.MIN_SECRET_BYTES - 1)).requireValidForV2());
		assertDoesNotThrow(
				() -> properties("x".repeat(SecurityProperties.MIN_SECRET_BYTES)).requireValidForV2());
	}

	@Test
	void v2RequiresAPositiveTokenLifetime() {
		SecurityProperties properties = new SecurityProperties(
				new SecurityProperties.Jwt("x".repeat(32), "contentfilter", Duration.ZERO), NO_ADMIN);

		assertThrows(IllegalStateException.class, properties::requireValidForV2);
	}

	private static SecurityProperties properties(String secret) {
		return new SecurityProperties(
				new SecurityProperties.Jwt(secret, "contentfilter", Duration.ofMinutes(30)), NO_ADMIN);
	}
}
