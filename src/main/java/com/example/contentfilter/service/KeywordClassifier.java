package com.example.contentfilter.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.example.contentfilter.dto.ClassificationResponse;
import com.example.contentfilter.exception.InvalidClassificationRequestException;
import org.springframework.stereotype.Service;

/**
 * Demonstration classifier that assigns categories using predefined keywords.
 *
 * <p>Matching is case-insensitive. Confidence values are randomly generated
 * placeholders and do not represent probabilities from a trained model.</p>
 */
@Service
public class KeywordClassifier implements LegacyClassificationService {

	private static final int MAX_TEXT_LENGTH = 10_000;

	/**
	 * Classifies text as abusive, professional, or neutral.
	 *
	 * @param text content to inspect
	 * @return classification category, confidence, and timestamp
	 * @throws InvalidClassificationRequestException if text is blank or too long
	 */
	@Override
	public ClassificationResponse classify(String text) {
		validate(text);

		String normalizedText = text.toLowerCase(Locale.ROOT);
		if (normalizedText.contains("stupid") || normalizedText.contains("idiot")) {
			return response("abusive");
		}

		if (normalizedText.contains("regards")) {
			return response("professional");
		}

		return response("neutral");
	}

	/**
	 * Enforces the input constraints shared by the keyword rules.
	 */
	private void validate(String text) {
		if (text == null || text.isBlank()) {
			throw new InvalidClassificationRequestException("Request body must not be empty.");
		}

		if (text.length() > MAX_TEXT_LENGTH) {
			throw new InvalidClassificationRequestException(
					"Text must not exceed " + MAX_TEXT_LENGTH + " characters.");
		}
	}

	/**
	 * Builds classification metadata for a selected category.
	 */
	private ClassificationResponse response(String category) {
		double confidence = ThreadLocalRandom.current().nextDouble(0.80, 0.99);
		String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		return new ClassificationResponse(category, confidence, timestamp);
	}
}
