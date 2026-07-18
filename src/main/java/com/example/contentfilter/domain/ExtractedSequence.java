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
 */
public record ExtractedSequence(
		String sequenceId,
		int sheetIndex,
		int rowIndex,
		List<Integer> sourceColumnIndexes,
		String text) {

	public ExtractedSequence {
		sourceColumnIndexes = List.copyOf(sourceColumnIndexes);
	}
}
