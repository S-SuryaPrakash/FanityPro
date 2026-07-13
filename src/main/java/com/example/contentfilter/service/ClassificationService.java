package com.example.contentfilter.service;

import com.example.contentfilter.dto.ClassificationResponse;

/**
 * Strategy contract for components that classify text content.
 *
 * <p>Workflow services depend on this abstraction so the current keyword
 * implementation can later be replaced by another classification engine.</p>
 */
public interface ClassificationService {

	/**
	 * Classifies a single text value.
	 *
	 * @param text content to classify
	 * @return category and classification metadata
	 */
	ClassificationResponse classify(String text);
}
