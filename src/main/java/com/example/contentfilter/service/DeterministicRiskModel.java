package com.example.contentfilter.service;

import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.ModelPrediction;
import com.example.contentfilter.domain.RiskCategory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Predictable Module 3 adapter used to test orchestration and policy behavior.
 * It is not a trained model and must be replaced before a production release.
 */
@Component
@ConditionalOnProperty(
		name = "content-filter.classification.provider",
		havingValue = "deterministic",
		matchIfMissing = true)
public class DeterministicRiskModel implements RiskModel {

	static final String MODEL_ID = "deterministic-risk-adapter";
	static final String MODEL_REVISION = "module-3-v1";

	@Override
	public List<ModelPrediction> predict(List<ExtractedSequence> sequences) {
		List<ModelPrediction> predictions = new ArrayList<>(sequences.size());
		for (ExtractedSequence sequence : sequences) {
			String text = sequence.text().toLowerCase(Locale.ROOT);
			EnumMap<RiskCategory, Double> scores = baseScores();
			setWhenPresent(scores, RiskCategory.THREAT, text, 0.96,
					"kill you", "hurt you", "death threat");
			setWhenPresent(scores, RiskCategory.HATE_OR_IDENTITY_ATTACK, text, 0.93,
					"identity attack", "hate speech");
			setWhenPresent(scores, RiskCategory.HARASSMENT_OR_INSULT, text, 0.91,
					"stupid", "idiot", "worthless");
			setWhenPresent(scores, RiskCategory.OBSCENE_OR_PROFANE, text, 0.90,
					"damn", "profanity example");
			setWhenPresent(scores, RiskCategory.GENERAL_TOXICITY, text, 0.86,
					"toxic message", "hostile message");
			predictions.add(new ModelPrediction(
					sequence.sequenceId(), scores, false, MODEL_ID, MODEL_REVISION));
		}
		return List.copyOf(predictions);
	}

	private EnumMap<RiskCategory, Double> baseScores() {
		EnumMap<RiskCategory, Double> scores = new EnumMap<>(RiskCategory.class);
		for (RiskCategory category : RiskCategory.values()) {
			if (category.isDetectedRisk()) {
				scores.put(category, 0.01);
			}
		}
		return scores;
	}

	private void setWhenPresent(
			EnumMap<RiskCategory, Double> scores,
			RiskCategory category,
			String text,
			double score,
			String... phrases) {
		for (String phrase : phrases) {
			if (text.contains(phrase)) {
				scores.put(category, score);
				return;
			}
		}
	}
}
