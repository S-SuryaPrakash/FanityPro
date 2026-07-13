package com.example.contentfilter.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides lightweight operational and demonstration endpoints.
 */
@RestController
public class HealthController {

	/**
	 * Reports whether the application can accept HTTP requests.
	 *
	 * @return a simple status payload
	 */
	@GetMapping("/health")
	public ResponseEntity<Map<String, String>> health() {
		return ResponseEntity.ok(Map.of("status", "UP"));
	}

	/**
	 * Returns placeholder report content until report generation is implemented.
	 *
	 * @return demonstration report response
	 */
	@GetMapping("/download")
	public ResponseEntity<String> downloadReport() {
		return ResponseEntity.ok()
				.header("X-Report-Generated-By", "Spring-Boot-App")
				.body("Report Content");
	}
}
