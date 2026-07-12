package com.example.contentfilter.controller;

import com.example.contentfilter.dto.UploadResponse;
import com.example.contentfilter.service.ExcelPreviewService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Iterator;
@RestController
public class UploadController {
	private final ExcelPreviewService excelPreviewService;

	public UploadController(ExcelPreviewService excelPreviewService) {
		this.excelPreviewService = excelPreviewService;
	}

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public UploadResponse uploadFile(MultipartHttpServletRequest request) {
		// Read the uploaded file names from the multipart request.
		Iterator<String> fileNames = request.getFileNames();
		if (fileNames == null || !fileNames.hasNext()) {
			throw new IllegalArgumentException("No file part in the request");
		}

		// Use the first uploaded file for processing.
		String firstName = fileNames.next();
		MultipartFile file = request.getFile(firstName);
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Uploaded file must not be empty");
		}

		// Capture basic file metadata before parsing its contents.
		String filename = file.getOriginalFilename();
		String contentType = file.getContentType();
		String preview = null;

		// Only attempt to parse Excel files
		if ((filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls")))
				|| (contentType != null && contentType.contains("spreadsheet"))) {
			preview = excelPreviewService.createPreview(file);
		}

		return new UploadResponse(
				filename,
				file.getSize(),
				contentType,
				preview
		);
	}
}
