package com.example.contentfilter.domain;

/**
 * Downloadable workbook produced by the complete V1 classification workflow.
 * The byte content is defensively copied so callers cannot mutate the result.
 */
public record ClassifiedWorkbook(String filename, byte[] content) {

	public ClassifiedWorkbook {
		if (filename == null || filename.isBlank() || !filename.endsWith(".xlsx")) {
			throw new IllegalArgumentException("A classified .xlsx filename is required.");
		}
		if (content == null || content.length == 0) {
			throw new IllegalArgumentException("Classified workbook content is required.");
		}
		content = content.clone();
	}

	@Override
	public byte[] content() {
		return content.clone();
	}
}
