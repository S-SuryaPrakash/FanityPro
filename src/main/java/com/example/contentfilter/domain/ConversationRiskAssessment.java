package com.example.contentfilter.domain;

import java.util.List;

/**
 * Correlated source sequences and policy decisions retained for later workbook
 * report generation.
 */
public record ConversationRiskAssessment(
		List<ExtractedSequence> sequences,
		List<ClassificationResult> results) {

	public ConversationRiskAssessment {
		sequences = List.copyOf(sequences);
		results = List.copyOf(results);
		if (sequences.size() != results.size()) {
			throw new IllegalArgumentException("Every extracted sequence requires one result.");
		}
		for (int index = 0; index < sequences.size(); index++) {
			if (!sequences.get(index).sequenceId().equals(results.get(index).sequenceId())) {
				throw new IllegalArgumentException("Sequence and result IDs must remain correlated.");
			}
		}
	}
}
