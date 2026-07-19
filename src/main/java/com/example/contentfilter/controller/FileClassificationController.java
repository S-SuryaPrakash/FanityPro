package com.example.contentfilter.controller;

import com.example.contentfilter.domain.ClassifiedWorkbook;
import com.example.contentfilter.service.WorkbookClassificationService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Versioned HTTP boundary for the complete workbook-classification workflow. */
@RestController
public class FileClassificationController {

	public static final String XLSX_MEDIA_TYPE =
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private final WorkbookClassificationService workbookClassificationService;

	public FileClassificationController(
			WorkbookClassificationService workbookClassificationService) {
		this.workbookClassificationService = workbookClassificationService;
	}

	/**
	 * Classifies the standard conversation workbook and returns an annotated copy.
	 * The response is never cacheable because it can contain sensitive messages.
	 */
	@PostMapping(
			value = "/api/v1/files/classify",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
			produces = XLSX_MEDIA_TYPE)
	public ResponseEntity<byte[]> classify(
			@RequestPart(value = "file", required = false) MultipartFile file) {
		ClassifiedWorkbook workbook = workbookClassificationService.classify(file);
		byte[] content = workbook.content();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE));
		headers.setContentDisposition(ContentDisposition.attachment()
				.filename(workbook.filename(), StandardCharsets.UTF_8)
				.build());
		headers.setContentLength(content.length);
		headers.setCacheControl("no-store");
		headers.setPragma("no-cache");
		headers.set("X-Content-Type-Options", "nosniff");
		return ResponseEntity.ok().headers(headers).body(content);
	}
}
