package com.example.contentfilter.service;

import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ExtractedSequence;
import java.util.List;

/** Batch-first application port for the production risk-classification flow. */
public interface ClassificationService {

	/**
	 * Classifies a bounded batch and returns one correlated result per sequence.
	 * Implementations must not depend on response ordering from a model provider.
	 */
	List<ClassificationResult> classify(List<ExtractedSequence> sequences);
}
