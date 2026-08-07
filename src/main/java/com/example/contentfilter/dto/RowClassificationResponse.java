package com.example.contentfilter.dto;

/**
 * Classification result associated with its source worksheet row.
 *
 * @deprecated Used only by the legacy {@link com.example.contentfilter.service.UploadClassificationService} chain.
 * @param rowNumber one-based physical worksheet row number
 * @param text extracted row text
 * @param classification classification produced for the row
 */
@Deprecated(forRemoval = true)
public record RowClassificationResponse(
		int rowNumber,
		String text,
		ClassificationResponse classification) {
}
