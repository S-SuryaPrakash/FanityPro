package com.example.contentfilter.service;

import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.ModelPrediction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Correlates batch model evidence by sequence ID and applies the V1 policy. */
@Service
public class RiskClassificationService implements ClassificationService {

	private final RiskModel riskModel;
	private final ClassificationPolicy policy;

	public RiskClassificationService(RiskModel riskModel, ClassificationPolicy policy) {
		this.riskModel = riskModel;
		this.policy = policy;
	}

	@Override
	public List<ClassificationResult> classify(List<ExtractedSequence> sequences) {
		if (sequences == null) {
			throw new IllegalArgumentException("Sequence batch must not be null.");
		}
		if (sequences.isEmpty()) {
			return List.of();
		}

		Set<String> requestedIds = new HashSet<>();
		for (ExtractedSequence sequence : sequences) {
			if (sequence == null || !requestedIds.add(sequence.sequenceId())) {
				throw new IllegalArgumentException("Sequence IDs must be present and unique.");
			}
		}

		List<ModelPrediction> predictions = riskModel.predict(List.copyOf(sequences));
		if (predictions == null) {
			throw new IllegalStateException("The model returned no prediction batch.");
		}
		Map<String, ModelPrediction> bySequenceId = new HashMap<>();
		for (ModelPrediction prediction : predictions) {
			if (prediction == null || bySequenceId.put(prediction.sequenceId(), prediction) != null) {
				throw new IllegalStateException("The model returned a missing or duplicate sequence ID.");
			}
		}
		if (!bySequenceId.keySet().equals(requestedIds)) {
			throw new IllegalStateException("The model response IDs do not match the request IDs.");
		}

		return sequences.stream()
				.map(sequence -> policy.decide(bySequenceId.get(sequence.sequenceId())))
				.toList();
	}
}
