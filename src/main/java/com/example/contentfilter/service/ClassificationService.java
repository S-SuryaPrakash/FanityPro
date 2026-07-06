package com.example.contentfilter.service;

import java.util.concurrent.ThreadLocalRandom;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import com.example.contentfilter.dto.ClassificationResponse;
import com.example.contentfilter.exception.InvalidClassificationRequestException;
import org.springframework.stereotype.Service;

@Service
public class ClassificationService {

	private static final int MAX_TEXT_LENGTH = 10_000;

	public ClassificationResponse classify(String text) {
		validate(text);

		text = text.toLowerCase(java.util.Locale.ROOT);
		if (text.contains("stupid")) {
			return response("abusive");
		}

		if (text.contains("idiot")) {
			return response("abusive");
		}

		if (text.contains("regards")) {
			return response("professional");
		}

		return response("neutral");
	}

	private void validate(String text) {
		// if (text == null || text.trim().isEmpty()) {
		// 	throw new InvalidClassificationRequestException("Request body must not be empty.");
		// }

		// if (text.length() > MAX_TEXT_LENGTH) {
		// 	throw new InvalidClassificationRequestException("Text must not exceed 10,000 characters.");
		// }
		if (text == null || text.isBlank()) {
    	throw new InvalidClassificationRequestException("Text cannot be blank");
	}}

	private ClassificationResponse response(String category) {
		double confidence = ThreadLocalRandom.current().nextDouble(0.80, 0.99);
		String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		ClassificationResponse response = new ClassificationResponse(category, confidence, timestamp);

		return response.categoryLower();
	}
}
