package com.example.contentfilter.controller;

import com.example.contentfilter.audit.AuditService;
import com.example.contentfilter.audit.AuditService.AuditEventView;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only audit trail; restricted to ADMIN in {@code V2SecurityConfiguration}. */
@RestController
@Profile("v2")
@RequestMapping("/api/v2/audit-events")
public class AuditEventController {

	private final AuditService audit;

	public AuditEventController(AuditService audit) {
		this.audit = audit;
	}

	@GetMapping
	public PageResponse<AuditEventView> list(
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) UUID entityId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return PageResponse.from(audit.list(entityType, entityId, page, size));
	}
}
