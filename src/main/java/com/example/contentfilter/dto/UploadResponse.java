package com.example.contentfilter.dto;

import java.util.List;

/**
 * Response returned after a file upload.
 *
 * @param fileName name of the uploaded file
 * @param size size of the uploaded file in bytes
 * @param contentType reported MIME type
 * @param results classifications for each non-empty row in the workbook
 */
public record UploadResponse(
		String fileName,
		long size,
		String contentType,
		List<RowClassificationResponse> results) {
}
