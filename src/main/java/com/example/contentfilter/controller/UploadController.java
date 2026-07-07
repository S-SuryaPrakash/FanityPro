package com.example.contentfilter.controller;

import com.example.contentfilter.dto.UploadResponse;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UploadController {

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public UploadResponse uploadFile(@RequestParam("file") MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Uploaded file must not be empty");
		}

		return new UploadResponse(
				file.getOriginalFilename(),
				file.getSize(),
				file.getContentType()
		);
	}
}