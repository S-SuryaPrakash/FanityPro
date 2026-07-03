package com.example.contentfilter.controller;

import com.example.contentfilter.dto.ClassificationRequest;
import com.example.contentfilter.dto.ClassificationResponse;
import com.example.contentfilter.service.ClassificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClassifyController {

	private final ClassificationService classificationService;

	public ClassifyController(ClassificationService classificationService) {
		this.classificationService = classificationService;
	}

	@PostMapping("/classify")
	public ClassificationResponse classify(@RequestBody ClassificationRequest request) {
		String text = request == null ? null : request.text();
		return classificationService.classify(text);
	}
}
