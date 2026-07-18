package com.example.contentfilter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contentfilter.config.RiskPolicyProperties;
import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ModelPrediction;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies deterministic, auditable decisions independently of a model. */
class VersionedRiskPolicyTests {

	private final VersionedRiskPolicy policy = new VersionedRiskPolicy(properties());

	@Test
	void routesDetectedThreatToPriorityReview() {
		ClassificationResult result = policy.decide(prediction(scores(
				RiskCategory.THREAT, 0.92), false));

		assertEquals(RiskCategory.THREAT, result.primaryCategory());
		assertEquals(RiskSeverity.CRITICAL, result.severity());
		assertTrue(result.manualReviewRequired());
		assertEquals("risk-policy-test-v1", result.policyVersion());
	}

	@Test
	void usesNoAutomatedFlagWithoutClaimingSafety() {
		ClassificationResult result = policy.decide(prediction(scores(null, 0.0), false));

		assertEquals(RiskCategory.NO_AUTOMATED_FLAG, result.primaryCategory());
		assertEquals(RiskSeverity.NONE, result.severity());
		assertFalse(result.manualReviewRequired());
		assertNull(result.reviewReason());
	}

	@Test
	void routesNearThresholdConflictingAndTruncatedEvidenceToManualReview() {
		ClassificationResult nearThreshold = policy.decide(prediction(scores(
				RiskCategory.GENERAL_TOXICITY, 0.68), false));
		assertEquals(RiskCategory.MANUAL_REVIEW, nearThreshold.primaryCategory());

		EnumMap<RiskCategory, Double> conflicting = scores(RiskCategory.THREAT, 0.91);
		conflicting.put(RiskCategory.HATE_OR_IDENTITY_ATTACK, 0.89);
		ClassificationResult conflict = policy.decide(prediction(conflicting, false));
		assertEquals(RiskCategory.MANUAL_REVIEW, conflict.primaryCategory());

		ClassificationResult truncated = policy.decide(prediction(scores(null, 0.0), true));
		assertEquals(RiskCategory.MANUAL_REVIEW, truncated.primaryCategory());
		assertEquals(RiskSeverity.HIGH, truncated.severity());
	}

	private ModelPrediction prediction(Map<RiskCategory, Double> scores, boolean truncated) {
		return new ModelPrediction("sequence-1", scores, truncated, "test-model", "revision-1");
	}

	private EnumMap<RiskCategory, Double> scores(RiskCategory elevated, double score) {
		EnumMap<RiskCategory, Double> scores = new EnumMap<>(RiskCategory.class);
		for (RiskCategory category : RiskCategory.values()) {
			if (category.isDetectedRisk()) {
				scores.put(category, 0.01);
			}
		}
		if (elevated != null) {
			scores.put(elevated, score);
		}
		return scores;
	}

	private RiskPolicyProperties properties() {
		return new RiskPolicyProperties(
				"risk-policy-test-v1",
				0.05,
				Map.of(
						RiskCategory.THREAT, 0.80,
						RiskCategory.HATE_OR_IDENTITY_ATTACK, 0.80,
						RiskCategory.HARASSMENT_OR_INSULT, 0.75,
						RiskCategory.OBSCENE_OR_PROFANE, 0.75,
						RiskCategory.GENERAL_TOXICITY, 0.70),
				List.of(
						RiskCategory.THREAT,
						RiskCategory.HATE_OR_IDENTITY_ATTACK,
						RiskCategory.HARASSMENT_OR_INSULT,
						RiskCategory.OBSCENE_OR_PROFANE,
						RiskCategory.GENERAL_TOXICITY));
	}
}
