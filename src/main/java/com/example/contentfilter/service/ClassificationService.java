package com.example.contentfilter.service;

import com.example.contentfilter.dto.ClassificationResponse;

/**
 * Contract for components that classify text content.
 */
public interface ClassificationService {

	ClassificationResponse classify(String text);
}
