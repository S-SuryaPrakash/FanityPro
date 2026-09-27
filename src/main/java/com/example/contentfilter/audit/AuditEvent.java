package com.example.contentfilter.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One append-only audit record. Immutable in Hibernate and, independently,
 * protected against UPDATE/DELETE by a database trigger.
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	/** Null only for system actions such as the bootstrap admin creation. */
	@Column(name = "actor_id")
	private UUID actorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 64)
	private AuditAction action;

	@Column(name = "entity_type", nullable = false, length = 64)
	private String entityType;

	@Column(name = "entity_id")
	private UUID entityId;

	@Column(name = "correlation_id", length = 64)
	private String correlationId;

	@Column(nullable = false)
	private String details;

	protected AuditEvent() {
	}

	AuditEvent(
			Instant occurredAt,
			UUID actorId,
			AuditAction action,
			String entityType,
			UUID entityId,
			String correlationId,
			String details) {
		this.occurredAt = occurredAt;
		this.actorId = actorId;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.correlationId = correlationId;
		this.details = details;
	}

	public UUID getId() {
		return id;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public UUID getActorId() {
		return actorId;
	}

	public AuditAction getAction() {
		return action;
	}

	public String getEntityType() {
		return entityType;
	}

	public UUID getEntityId() {
		return entityId;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public String getDetails() {
		return details;
	}
}
