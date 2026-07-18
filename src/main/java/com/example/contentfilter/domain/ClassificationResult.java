package com.example.contentfilter.domain;

import java.util.Map;

/** Auditable decision created from model evidence and a versioned policy. */
public record ClassificationResult(
		String sequenceId,
		RiskCategory primaryCategory,
		RiskSeverity severity,
		double confidence,
		Map<RiskCategory, Double> scores,
		boolean manualReviewRequired,
		String reviewReason,
		String modelId,
		String modelRevision,
		String policyVersion) {

	public ClassificationResult {
		if (sequenceId == null || sequenceId.isBlank()) {
			throw new IllegalArgumentException("Sequence ID must not be blank.");
		}
		if (primaryCategory == null || severity == null) {
			throw new IllegalArgumentException("Category and severity are required.");
		}
		if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
			throw new IllegalArgumentException("Confidence must be between 0 and 1.");
		}
		scores = Map.copyOf(scores);
		if (manualReviewRequired && (reviewReason == null || reviewReason.isBlank())) {
			throw new IllegalArgumentException("A manual-review decision requires a reason.");
		}
		if (modelId == null || modelId.isBlank() || modelRevision == null
				|| modelRevision.isBlank() || policyVersion == null || policyVersion.isBlank()) {
			throw new IllegalArgumentException("Model and policy versions are required.");
		}
	}
}
