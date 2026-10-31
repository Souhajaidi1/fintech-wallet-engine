-- ---------------------------------------------------------------------------
-- V3: idempotency keys and audit trail (see ADR-003)
-- ---------------------------------------------------------------------------

CREATE TABLE idempotency_key (
    id                UUID         PRIMARY KEY,
    key_value         VARCHAR(120) NOT NULL,
    user_id           UUID         NOT NULL,
    endpoint          VARCHAR(80)  NOT NULL,
    request_hash      CHAR(64)     NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    response_status   INT,
    response_body     TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    -- The uniqueness that makes the whole mechanism work. Two concurrent
    -- requests carrying the same key race to INSERT; exactly one wins.
    CONSTRAINT uq_idempotency_scope UNIQUE (key_value, user_id, endpoint)
);

CREATE INDEX ix_idempotency_created ON idempotency_key (created_at);

CREATE TABLE audit_event (
    id          UUID         PRIMARY KEY,
    event_type  VARCHAR(40)  NOT NULL,
    actor_id    UUID,
    subject_id  UUID,
    outcome     VARCHAR(16)  NOT NULL,
    detail      VARCHAR(500),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX ix_audit_actor   ON audit_event (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_subject ON audit_event (subject_id, occurred_at DESC);
