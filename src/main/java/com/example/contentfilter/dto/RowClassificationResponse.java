package com.example.contentfilter.dto;

/**
 * Classification result associated with its source worksheet row.
 *
 * @param rowNumber one-based worksheet row number among extracted non-empty rows
 * @param text extracted row text
 * @param classification classification produced for the row
 */
public record RowClassificationResponse(
		int rowNumber,
		String text,
		ClassificationResponse classification) {
}
