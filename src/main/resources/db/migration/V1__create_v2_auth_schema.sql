-- V2 foundation: local account persistence. Later migrations add cases,
-- decisions, notes, and immutable audit events without rewriting this history.
CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_email_not_blank CHECK (length(trim(email)) > 0),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'ANALYST'))
);

CREATE INDEX ix_app_users_role ON app_users (role);
