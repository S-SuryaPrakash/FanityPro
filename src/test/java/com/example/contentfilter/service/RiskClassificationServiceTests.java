package com.example.contentfilter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.ModelPrediction;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies batch correlation without trusting provider response order. */
class RiskClassificationServiceTests {

	@Test
	void correlatesOutOfOrderPredictionsBySequenceId() {
		ExtractedSequence first = sequence("first", 0);
		ExtractedSequence second = sequence("second", 1);
		RiskModel outOfOrderModel = ignored -> List.of(prediction("second"), prediction("first"));
		ClassificationPolicy policy = prediction -> result(prediction.sequenceId());
		ClassificationService service = new RiskClassificationService(outOfOrderModel, policy);

		List<ClassificationResult> results = service.classify(List.of(first, second));

		assertEquals(List.of("first", "second"),
				results.stream().map(ClassificationResult::sequenceId).toList());
	}

	@Test
	void rejectsModelResponsesWithMismatchedIds() {
		RiskModel wrongModel = ignored -> List.of(prediction("unexpected"));
		ClassificationService service = new RiskClassificationService(
				wrongModel, prediction -> result(prediction.sequenceId()));

		assertThrows(IllegalStateException.class,
				() -> service.classify(List.of(sequence("requested", 0))));
	}

	private ExtractedSequence sequence(String id, int row) {
		return new ExtractedSequence(id, 0, row, List.of(0), "message");
	}

	private ModelPrediction prediction(String id) {
		return new ModelPrediction(
				id, Map.of(RiskCategory.GENERAL_TOXICITY, 0.01), false, "model", "revision");
	}

	private ClassificationResult result(String id) {
		return new ClassificationResult(
				id, RiskCategory.NO_AUTOMATED_FLAG, RiskSeverity.NONE, 0.99,
				Map.of(RiskCategory.GENERAL_TOXICITY, 0.01), false, null,
				"model", "revision", "policy");
	}
}
