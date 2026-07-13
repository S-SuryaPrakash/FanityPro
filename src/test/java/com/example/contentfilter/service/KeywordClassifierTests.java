package com.example.contentfilter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contentfilter.dto.ClassificationResponse;
import com.example.contentfilter.exception.InvalidClassificationRequestException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for keyword rules, response metadata, and input validation.
 */
class KeywordClassifierTests {

	private final ClassificationService classifier = new KeywordClassifier();

	/**
	 * Verifies every category currently supported by the keyword strategy.
	 */
	@Test
	void classifiesSupportedCategories() {
		assertEquals("abusive", classifier.classify("You are stupid").category());
		assertEquals("professional", classifier.classify("Kind regards").category());
		assertEquals("neutral", classifier.classify("Hello world").category());
	}

	/**
	 * Verifies the demonstration confidence range and timestamp presence.
	 */
	@Test
	void returnsExpectedMetadata() {
		ClassificationResponse response = classifier.classify("Hello world");

		assertTrue(response.confidence() >= 0.80 && response.confidence() < 0.99);
		assertTrue(response.timestamp() != null && !response.timestamp().isBlank());
	}

	/**
	 * Verifies that blank input is rejected before keyword matching.
	 */
	@Test
	void rejectsBlankText() {
		assertThrows(InvalidClassificationRequestException.class, () -> classifier.classify("  "));
	}
}
