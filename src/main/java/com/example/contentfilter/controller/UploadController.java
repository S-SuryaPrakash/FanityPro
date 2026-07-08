package com.example.contentfilter.controller;

import com.example.contentfilter.dto.UploadResponse;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Iterator;

@RestController
public class UploadController {

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public UploadResponse uploadFile(MultipartHttpServletRequest request) {
		Iterator<String> fileNames = request.getFileNames();
		if (fileNames == null || !fileNames.hasNext()) {
			throw new IllegalArgumentException("No file part in the request");
		}

		String firstName = fileNames.next();
		MultipartFile file = request.getFile(firstName);
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