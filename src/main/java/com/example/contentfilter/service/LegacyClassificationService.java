package com.example.contentfilter.service;

import com.example.contentfilter.dto.ClassificationResponse;

/**
 * Compatibility port used only by the current JSON prototype endpoint.
 * New workflow code must use the batch-first {@link ClassificationService}.
 */
public interface LegacyClassificationService {

	ClassificationResponse classify(String text);
}
