package com.example.contentfilter.domain;

import java.util.EnumMap;
import java.util.Map;

/** Provider-neutral multi-label evidence returned by a model adapter. */
public record ModelPrediction(
		String sequenceId,
		Map<RiskCategory, Double> scores,
		boolean inputTruncated,
		String modelId,
		String modelRevision) {

	public ModelPrediction {
		requireText(sequenceId, "Sequence ID");
		requireText(modelId, "Model ID");
		requireText(modelRevision, "Model revision");
		if (scores == null || scores.isEmpty()) {
			throw new IllegalArgumentException("Model scores must not be empty.");
		}
		EnumMap<RiskCategory, Double> copy = new EnumMap<>(RiskCategory.class);
		for (Map.Entry<RiskCategory, Double> entry : scores.entrySet()) {
			RiskCategory category = entry.getKey();
			Double score = entry.getValue();
			if (category == null || !category.isDetectedRisk()) {
				throw new IllegalArgumentException("Model scores may contain detected-risk categories only.");
			}
			if (score == null || !Double.isFinite(score) || score < 0.0 || score > 1.0) {
				throw new IllegalArgumentException("Every model score must be between 0 and 1.");
			}
			copy.put(category, score);
		}
		scores = Map.copyOf(copy);
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank.");
		}
	}
}
