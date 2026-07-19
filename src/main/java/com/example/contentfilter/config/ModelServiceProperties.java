package com.example.contentfilter.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection, resilience, batching, and release-gate settings for FastAPI.
 *
 * <p>The expected model identity prevents a changed remote deployment from
 * silently supplying evidence from a different model revision.</p>
 */
@Validated
@ConfigurationProperties("content-filter.classification.model-service")
public record ModelServiceProperties(
		@NotNull URI baseUrl,
		@NotBlank String expectedModelId,
		@NotBlank @Pattern(regexp = "[0-9a-f]{40}") String expectedRevision,
		@NotNull Duration connectTimeout,
		@NotNull Duration readTimeout,
		@Min(1) @Max(32) int batchSize,
		@Min(1) @Max(2) int maxAttempts,
		boolean allowProvisional) {

	public ModelServiceProperties {
		validateBaseUrl(baseUrl);
		validateDuration(connectTimeout, Duration.ofSeconds(10), "Connect timeout");
		validateDuration(readTimeout, Duration.ofSeconds(60), "Read timeout");
		if (connectTimeout != null && readTimeout != null && maxAttempts > 0
				&& connectTimeout.plus(readTimeout).multipliedBy(maxAttempts)
						.compareTo(Duration.ofSeconds(60)) > 0) {
			throw new IllegalArgumentException(
					"Configured model attempts must fit within the 60-second V1 request budget.");
		}
	}

	private static void validateBaseUrl(URI value) {
		if (value == null) {
			return;
		}
		String scheme = value.getScheme();
		boolean supportedScheme = "http".equalsIgnoreCase(scheme)
				|| "https".equalsIgnoreCase(scheme);
		boolean rootPath = value.getPath() == null
				|| value.getPath().isEmpty()
				|| "/".equals(value.getPath());
		if (!supportedScheme || value.getHost() == null || value.getUserInfo() != null
				|| value.getQuery() != null || value.getFragment() != null || !rootPath) {
			throw new IllegalArgumentException(
					"Model service base URL must be an HTTP(S) origin without credentials or a path.");
		}
	}

	private static void validateDuration(Duration value, Duration maximum, String field) {
		if (value != null && (value.isZero() || value.isNegative() || value.compareTo(maximum) > 0)) {
			throw new IllegalArgumentException(
					field + " must be positive and no greater than " + maximum.toSeconds() + " seconds.");
		}
	}
}
