package com.example.contentfilter.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.example.contentfilter.dto.ClassificationResponse;
import com.example.contentfilter.exception.InvalidClassificationRequestException;
import org.springframework.stereotype.Service;

/**
 * Classifies text using a small set of predefined keywords.
 */
@Service
public class KeywordClassifier implements ClassificationService {

	private static final int MAX_TEXT_LENGTH = 10_000;

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

	private void validate(String text) {
		if (text == null || text.isBlank()) {
			throw new InvalidClassificationRequestException("Request body must not be empty.");
		}

		if (text.length() > MAX_TEXT_LENGTH) {
			throw new InvalidClassificationRequestException(
					"Text must not exceed " + MAX_TEXT_LENGTH + " characters.");
		}
	}

	private ClassificationResponse response(String category) {
		double confidence = ThreadLocalRandom.current().nextDouble(0.80, 0.99);
		String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		return new ClassificationResponse(category, confidence, timestamp);
	}
}
