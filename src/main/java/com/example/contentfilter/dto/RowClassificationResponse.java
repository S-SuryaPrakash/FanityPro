package com.example.contentfilter.dto;

/**
 * Classification result associated with its source worksheet row.
 *
 * @param rowNumber one-based physical worksheet row number
 * @param text extracted row text
 * @param classification classification produced for the row
 */
public record RowClassificationResponse(
		int rowNumber,
		String text,
		ClassificationResponse classification) {
}
