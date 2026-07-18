package com.example.contentfilter.controller;

import com.example.contentfilter.dto.RowClassificationResponse;
import com.example.contentfilter.dto.UploadResponse;
import com.example.contentfilter.service.UploadClassificationService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** HTTP entry point for the current Excel upload-classification workflow. */
@RestController
public class UploadController {

	private final UploadClassificationService uploadClassificationService;

	public UploadController(UploadClassificationService uploadClassificationService) {
		this.uploadClassificationService = uploadClassificationService;
	}

	/**
	 * Processes the required multipart field named {@code file}.
	 *
	 * <p>Workbook validation and parsing belong to {@code ExcelService}; the
	 * controller only adapts HTTP input and output.</p>
	 */
	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public UploadResponse uploadFile(@RequestPart(value = "file", required = false) MultipartFile file) {
		List<RowClassificationResponse> results = uploadClassificationService.classifyRows(file);

		return new UploadResponse(
				file.getOriginalFilename(),
				file.getSize(),
				file.getContentType(),
				results);
	}
}
