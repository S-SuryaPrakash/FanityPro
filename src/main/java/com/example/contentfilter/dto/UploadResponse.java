package com.example.contentfilter.dto;

import java.util.List;

/**
 * Response returned after a V0 prototype file upload.
 *
 * @deprecated Used only by the legacy {@link com.example.contentfilter.controller.UploadController} endpoint.
 * @param fileName name of the uploaded file
 * @param size size of the uploaded file in bytes
 * @param contentType reported MIME type
 * @param results classifications for each non-empty row in the workbook
 */
@Deprecated(forRemoval = true)
public record UploadResponse(
		String fileName,
		long size,
		String contentType,
		List<RowClassificationResponse> results) {
}
