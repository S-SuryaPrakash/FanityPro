package com.example.contentfilter.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** Verifies invariants that cannot be represented by simple null checks. */
class UploadLimitsPropertiesTests {

	@Test
	void rejectsNonPositiveFileSizeAndProcessingTime() {
		assertThrows(IllegalArgumentException.class, () -> properties(DataSize.ofBytes(0), Duration.ofSeconds(1)));
		assertThrows(IllegalArgumentException.class, () -> properties(DataSize.ofBytes(1), Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> properties(DataSize.ofBytes(1), Duration.ofSeconds(-1)));
	}

	private UploadLimitsProperties properties(DataSize fileSize, Duration processingTime) {
		return new UploadLimitsProperties(fileSize, 1, 1, 1, 1, 1, 1, processingTime);
	}
}
