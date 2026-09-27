package com.example.contentfilter.audit;

import com.example.contentfilter.web.CorrelationIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Appends audit events inside the caller's transaction, so an action and its
 * audit record are committed or rolled back together.
 */
@Service
@Profile("v2")
public class AuditService {

	public static final int MAX_PAGE_SIZE = 100;

	private final AuditEventRepository repository;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public AuditService(AuditEventRepository repository, JsonMapper jsonMapper, Clock clock) {
		this.repository = repository;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	/**
	 * @param details small, non-sensitive values only; never message text,
	 *                passwords, or tokens
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(
			UUID actorId,
			AuditAction action,
			String entityType,
			UUID entityId,
			Map<String, ?> details) {
		repository.save(new AuditEvent(
				clock.instant(),
				actorId,
				action,
				entityType,
				entityId,
				MDC.get(CorrelationIdFilter.MDC_KEY),
				jsonMapper.writeValueAsString(details)));
	}

	/** Newest first; optionally narrowed to one entity, e.g. the history of a case. */
	@Transactional(readOnly = true)
	public Page<AuditEventView> list(String entityType, UUID entityId, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException(
					"page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE + ".");
		}
		if ((entityType == null) != (entityId == null)) {
			throw new IllegalArgumentException("entityType and entityId must be given together.");
		}
		PageRequest request = PageRequest.of(page, size,
				Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
		Page<AuditEvent> events = entityType == null
				? repository.findAll(request)
				: repository.findByEntityTypeAndEntityId(entityType, entityId, request);
		return events.map(event -> new AuditEventView(
				event.getId(),
				event.getOccurredAt(),
				event.getActorId(),
				event.getAction(),
				event.getEntityType(),
				event.getEntityId(),
				event.getCorrelationId(),
				jsonMapper.readTree(event.getDetails())));
	}

	public record AuditEventView(
			UUID id,
			Instant occurredAt,
			UUID actorId,
			AuditAction action,
			String entityType,
			UUID entityId,
			String correlationId,
			JsonNode details) {
	}
}
