package com.example.contentfilter.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Verifies security-sensitive URL and timeout boundaries for the model client. */
class ModelServicePropertiesTests {

	private static final String REVISION = "00eacca7ba7c09b1e82db508b03a901bf9cc89eb";

	@Test
	void rejectsCredentialsOrPathsInTheConfiguredModelOrigin() {
		assertThrows(IllegalArgumentException.class,
				() -> properties("http://user:secret@localhost:8000", Duration.ofSeconds(5)));
		assertThrows(IllegalArgumentException.class,
				() -> properties("http://localhost:8000/untrusted", Duration.ofSeconds(5)));
	}

	@Test
	void rejectsReadTimeoutBeyondTheV1RequestBudget() {
		assertThrows(IllegalArgumentException.class,
				() -> properties("http://localhost:8000", Duration.ofSeconds(61)));
	}

	@Test
	void rejectsCombinedRetriesBeyondTheV1RequestBudget() {
		assertThrows(IllegalArgumentException.class,
				() -> new ModelServiceProperties(
						URI.create("http://localhost:8000"),
						"minuva/MiniLMv2-toxic-jigsaw", REVISION,
						Duration.ofSeconds(5), Duration.ofSeconds(26), 32, 2, false));
	}

	private ModelServiceProperties properties(String url, Duration readTimeout) {
		return new ModelServiceProperties(
				URI.create(url), "minuva/MiniLMv2-toxic-jigsaw", REVISION,
				Duration.ofSeconds(1), readTimeout, 32, 2, false);
	}
}
