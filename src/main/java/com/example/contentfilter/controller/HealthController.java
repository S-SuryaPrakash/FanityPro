package com.example.contentfilter.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/health")
	public ResponseEntity<Map<String, String>> health() {
		return ResponseEntity.ok(Map.of("status", "UP"));
	}
	@GetMapping("/download")
public ResponseEntity<String> downloadReport() {
    return ResponseEntity.ok()
            .header("X-Report-Generated-By", "Spring-Boot-App")
            .body("Report Content");
}
}
