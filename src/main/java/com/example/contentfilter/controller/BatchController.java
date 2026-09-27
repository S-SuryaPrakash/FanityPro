package com.example.contentfilter.controller;

import com.example.contentfilter.review.ClassificationBatchService;
import com.example.contentfilter.review.ClassificationBatchService.BatchView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Workbook intake for the review workflow. Classification is synchronous for now. */
@RestController
@Profile("v2")
@RequestMapping("/api/v2/batches")
public class BatchController {

	private final ClassificationBatchService batches;

	public BatchController(ClassificationBatchService batches) {
		this.batches = batches;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<BatchView> upload(
			@AuthenticationPrincipal Jwt jwt,
			@RequestPart(value = "file", required = false) MultipartFile file) {
		BatchView batch = batches.upload(UUID.fromString(jwt.getSubject()), file);
		return ResponseEntity.created(URI.create("/api/v2/batches/" + batch.id())).body(batch);
	}

	@GetMapping("/{batchId}")
	public BatchView get(@PathVariable UUID batchId) {
		return batches.get(batchId);
	}

	@GetMapping
	public PageResponse<BatchView> list(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(batches.list(PageRequest.of(page, size,
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))));
	}
}
