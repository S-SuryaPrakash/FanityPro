package com.example.contentfilter.service;

import com.example.contentfilter.config.RiskPolicyProperties;
import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ModelPrediction;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Deterministic and versioned V1 policy for review routing. */
@Component
public class VersionedRiskPolicy implements ClassificationPolicy {

	private final RiskPolicyProperties properties;

	public VersionedRiskPolicy(RiskPolicyProperties properties) {
		this.properties = properties;
	}

	@Override
	public ClassificationResult decide(ModelPrediction prediction) {
		List<RiskCategory> passing = passingCategories(prediction);
		double highestScore = highestScore(prediction);

		if (prediction.inputTruncated()) {
			return result(prediction, RiskCategory.MANUAL_REVIEW, RiskSeverity.HIGH,
					highestScore, true, "Input was truncated; a person must review the complete message.");
		}

		if (hasConflictingSignals(prediction, passing)) {
			return result(prediction, RiskCategory.MANUAL_REVIEW,
					highestSeverity(passing), highestScore, true,
					"Multiple risk signals are too close to select one primary category reliably.");
		}

		if (!passing.isEmpty()) {
			RiskCategory primary = passing.getFirst();
			return result(prediction, primary, severityFor(primary),
					prediction.scores().get(primary), true,
					"The configured threshold for " + primary.name().toLowerCase() + " was exceeded.");
		}

		if (isNearAnyThreshold(prediction)) {
			return result(prediction, RiskCategory.MANUAL_REVIEW, RiskSeverity.LOW,
					highestScore, true,
					"A model score is close to a configured threshold.");
		}

		return result(prediction, RiskCategory.NO_AUTOMATED_FLAG, RiskSeverity.NONE,
				1.0 - highestScore, false, null);
	}

	private List<RiskCategory> passingCategories(ModelPrediction prediction) {
		List<RiskCategory> passing = new ArrayList<>();
		for (RiskCategory category : properties.precedence()) {
			if (prediction.scores().getOrDefault(category, 0.0)
					>= properties.thresholds().get(category)) {
				passing.add(category);
			}
		}
		return passing;
	}

	private boolean hasConflictingSignals(
			ModelPrediction prediction,
			List<RiskCategory> passing) {
		if (passing.size() < 2) {
			return false;
		}
		List<Double> scores = passing.stream()
				.map(prediction.scores()::get)
				.sorted(Comparator.reverseOrder())
				.toList();
		return scores.get(0) - scores.get(1) <= properties.uncertaintyMargin();
	}

	private boolean isNearAnyThreshold(ModelPrediction prediction) {
		return properties.thresholds().entrySet().stream().anyMatch(entry -> {
			double score = prediction.scores().getOrDefault(entry.getKey(), 0.0);
			return score < entry.getValue()
					&& score >= entry.getValue() - properties.uncertaintyMargin();
		});
	}

	private double highestScore(ModelPrediction prediction) {
		return prediction.scores().values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
	}

	private RiskSeverity highestSeverity(List<RiskCategory> categories) {
		return categories.stream().map(this::severityFor)
				.max(Comparator.comparingInt(Enum::ordinal)).orElse(RiskSeverity.LOW);
	}

	private RiskSeverity severityFor(RiskCategory category) {
		return switch (category) {
			case THREAT -> RiskSeverity.CRITICAL;
			case HATE_OR_IDENTITY_ATTACK, HARASSMENT_OR_INSULT -> RiskSeverity.HIGH;
			case OBSCENE_OR_PROFANE, GENERAL_TOXICITY -> RiskSeverity.MEDIUM;
			case MANUAL_REVIEW -> RiskSeverity.LOW;
			case NO_AUTOMATED_FLAG -> RiskSeverity.NONE;
		};
	}

	private ClassificationResult result(
			ModelPrediction prediction,
			RiskCategory category,
			RiskSeverity severity,
			double confidence,
			boolean reviewRequired,
			String reason) {
		return new ClassificationResult(
				prediction.sequenceId(), category, severity, confidence, prediction.scores(),
				reviewRequired, reason, prediction.modelId(), prediction.modelRevision(),
				properties.version());
	}
}
