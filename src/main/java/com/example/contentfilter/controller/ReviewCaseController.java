package com.example.contentfilter.controller;

import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import com.example.contentfilter.review.ReviewCase;
import com.example.contentfilter.review.ReviewCaseService;
import com.example.contentfilter.review.ReviewCaseService.CaseDetail;
import com.example.contentfilter.review.ReviewCaseService.CaseSummary;
import com.example.contentfilter.review.ReviewCaseStatus;
import com.example.contentfilter.review.ReviewDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The reviewer queue. Case detail includes message text, so responses are never cached. */
@RestController
@Profile("v2")
@RequestMapping("/api/v2/cases")
public class ReviewCaseController {

	private final ReviewCaseService cases;

	public ReviewCaseController(ReviewCaseService cases) {
		this.cases = cases;
	}

	@GetMapping
	public PageResponse<CaseSummary> list(
			@RequestParam(required = false) ReviewCaseStatus status,
			@RequestParam(required = false) UUID batchId,
			@RequestParam(required = false) RiskSeverity severity,
			@RequestParam(required = false) RiskCategory category,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(cases.list(status, batchId, severity, category, page, size));
	}

	@GetMapping("/{caseId}")
	public CaseDetail get(@PathVariable UUID caseId) {
		return cases.get(caseId);
	}

	@PostMapping("/{caseId}/decision")
	public CaseDetail decide(
			@PathVariable UUID caseId,
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody DecisionRequest request) {
		return cases.decide(caseId, UUID.fromString(jwt.getSubject()),
				request.decision(), request.finalCategory(), request.note());
	}

	/**
	 * @param finalCategory required for OVERRIDDEN; optional otherwise
	 * @param note required for OVERRIDDEN and DISMISSED
	 */
	public record DecisionRequest(
			@NotNull ReviewDecision decision,
			RiskCategory finalCategory,
			@Size(max = ReviewCase.MAX_NOTE_LENGTH) String note) {
	}
}
