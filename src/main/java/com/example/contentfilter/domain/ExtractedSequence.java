package com.example.contentfilter.domain;

import java.util.List;

/**
 * Text extracted from one physical workbook row together with its source cells.
 *
 * @param sequenceId stable identifier used to correlate later model results
 * @param sheetIndex zero-based physical worksheet index
 * @param rowIndex zero-based physical worksheet row index
 * @param sourceColumnIndexes zero-based columns whose values formed the text
 * @param text displayed cell values joined with tab characters
 * @param context optional source metadata that is not included in model text
 */
public record ExtractedSequence(
		String sequenceId,
		int sheetIndex,
		int rowIndex,
		List<Integer> sourceColumnIndexes,
		String text,
		ConversationContext context) {

	public ExtractedSequence {
		if (sequenceId == null || sequenceId.isBlank()) {
			throw new IllegalArgumentException("Sequence ID must not be blank.");
		}
		if (sheetIndex < 0 || rowIndex < 0) {
			throw new IllegalArgumentException("Worksheet coordinates must not be negative.");
		}
		if (sourceColumnIndexes == null || sourceColumnIndexes.isEmpty()) {
			throw new IllegalArgumentException("At least one source column is required.");
		}
		sourceColumnIndexes = List.copyOf(sourceColumnIndexes);
		if (sourceColumnIndexes.stream().anyMatch(index -> index == null || index < 0)) {
			throw new IllegalArgumentException("Source column indexes must not be negative.");
		}
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("Extracted text must not be blank.");
		}
		context = context == null ? ConversationContext.empty() : context;
	}

	/** Keeps the original simple row extraction source-compatible. */
	public ExtractedSequence(
			String sequenceId,
			int sheetIndex,
			int rowIndex,
			List<Integer> sourceColumnIndexes,
			String text) {
		this(sequenceId, sheetIndex, rowIndex, sourceColumnIndexes, text,
				ConversationContext.empty());
	}
}
