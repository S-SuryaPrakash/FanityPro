-- V2 secure review: classification batches, human review cases, and an
-- append-only audit trail. Only review-required messages are persisted;
-- messages with no automated flag never leave the uploaded workbook.

CREATE TABLE classification_batches (
    id UUID PRIMARY KEY,
    uploaded_by UUID NOT NULL REFERENCES app_users (id),
    original_filename VARCHAR(255) NOT NULL,
    total_sequences INTEGER NOT NULL,
    review_case_count INTEGER NOT NULL,
    model_id VARCHAR(255) NOT NULL,
    model_revision VARCHAR(255) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_batches_counts CHECK (
        total_sequences > 0 AND review_case_count BETWEEN 0 AND total_sequences)
);

CREATE INDEX ix_batches_created_at ON classification_batches (created_at DESC);

CREATE TABLE review_cases (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES classification_batches (id),
    sequence_id VARCHAR(128) NOT NULL,
    priority INTEGER NOT NULL,
    excel_row INTEGER NOT NULL,
    conversation_id TEXT,
    message_id TEXT,
    speaker_role TEXT,
    message_text TEXT NOT NULL,
    primary_category VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    -- Ordinal of severity, so queues sort CRITICAL first without string tricks.
    severity_rank INTEGER NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    score_threat DOUBLE PRECISION NOT NULL,
    score_hate_or_identity_attack DOUBLE PRECISION NOT NULL,
    score_harassment_or_insult DOUBLE PRECISION NOT NULL,
    score_obscene_or_profane DOUBLE PRECISION NOT NULL,
    score_general_toxicity DOUBLE PRECISION NOT NULL,
    review_reason TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    decision VARCHAR(16),
    final_category VARCHAR(32),
    decision_note TEXT,
    decided_by UUID REFERENCES app_users (id),
    decided_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_review_cases_batch_sequence UNIQUE (batch_id, sequence_id),
    CONSTRAINT ck_review_cases_primary_category CHECK (primary_category IN (
        'THREAT', 'HATE_OR_IDENTITY_ATTACK', 'HARASSMENT_OR_INSULT',
        'OBSCENE_OR_PROFANE', 'GENERAL_TOXICITY', 'MANUAL_REVIEW')),
    CONSTRAINT ck_review_cases_severity CHECK (
        severity IN ('NONE', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_review_cases_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_review_cases_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_review_cases_decision CHECK (
        decision IS NULL OR decision IN ('CONFIRMED', 'OVERRIDDEN', 'DISMISSED')),
    CONSTRAINT ck_review_cases_final_category CHECK (final_category IS NULL OR final_category IN (
        'THREAT', 'HATE_OR_IDENTITY_ATTACK', 'HARASSMENT_OR_INSULT',
        'OBSCENE_OR_PROFANE', 'GENERAL_TOXICITY', 'NO_AUTOMATED_FLAG')),
    -- A case is either untouched or fully decided; never half-resolved.
    CONSTRAINT ck_review_cases_resolution CHECK (
        (status = 'OPEN' AND decision IS NULL AND final_category IS NULL
            AND decided_by IS NULL AND decided_at IS NULL)
        OR (status = 'RESOLVED' AND decision IS NOT NULL AND final_category IS NOT NULL
            AND decided_by IS NOT NULL AND decided_at IS NOT NULL))
);

CREATE INDEX ix_review_cases_queue
    ON review_cases (status, severity_rank DESC, confidence DESC, created_at);
CREATE INDEX ix_review_cases_batch ON review_cases (batch_id, priority);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id UUID REFERENCES app_users (id),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID,
    correlation_id VARCHAR(64),
    -- JSON object of non-sensitive details; message text is never copied here.
    details TEXT NOT NULL
);

CREATE INDEX ix_audit_events_occurred_at ON audit_events (occurred_at DESC);
CREATE INDEX ix_audit_events_entity ON audit_events (entity_type, entity_id);

-- The audit trail is append-only at the database level, not just in code.
CREATE FUNCTION reject_audit_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_audit_event_mutation();
