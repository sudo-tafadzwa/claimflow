-- Read model and audit trail. The process state itself lives in Camunda; these tables are what the
-- UI and reports query, so dashboards never hit the engine.

CREATE TABLE policy (
    policy_number  TEXT PRIMARY KEY,
    holder_name    TEXT        NOT NULL,
    policy_type    TEXT        NOT NULL CHECK (policy_type IN ('TRAVEL', 'MOTOR', 'HOME')),
    cover_limit    NUMERIC(12, 2) NOT NULL CHECK (cover_limit > 0),
    deductible     NUMERIC(12, 2) NOT NULL CHECK (deductible >= 0),
    start_date     DATE        NOT NULL,
    end_date       DATE        NOT NULL,
    CHECK (end_date > start_date)
);

CREATE SEQUENCE claim_reference_seq START 1001;

CREATE TABLE claim (
    id                    UUID PRIMARY KEY,
    reference             TEXT        NOT NULL UNIQUE,
    claimant_name         TEXT        NOT NULL,
    claimant_email        TEXT        NOT NULL,
    submitted_by          TEXT        NOT NULL,
    status                TEXT        NOT NULL,
    policy_number         TEXT,
    policy_type           TEXT,
    incident_date         DATE,
    incident_type         TEXT,
    claimed_amount        NUMERIC(12, 2),
    payable_amount        NUMERIC(12, 2),
    fraud_score           NUMERIC(4, 3),
    decision_reason       TEXT,
    process_instance_key  BIGINT,
    payout_prepared_by    TEXT,
    payout_released_by    TEXT,
    payout_account        TEXT,
    payment_reference     TEXT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT      NOT NULL DEFAULT 0,
    CHECK (status IN ('SUBMITTED', 'START_FAILED', 'EXTRACTING', 'NEEDS_VERIFICATION', 'ASSESSING',
                      'UNDER_REVIEW', 'APPROVED', 'PAYOUT_PENDING_RELEASE', 'PAYING', 'PAID',
                      'REJECTED', 'PAYOUT_REVERSED'))
);

CREATE INDEX claim_submitted_by_idx ON claim (submitted_by);
CREATE INDEX claim_status_idx ON claim (status);
CREATE INDEX claim_policy_idx ON claim (policy_number);

CREATE TABLE claim_document (
    id              UUID PRIMARY KEY,
    claim_id        UUID        NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    filename        TEXT        NOT NULL,
    content_type    TEXT        NOT NULL,
    size_bytes      INTEGER     NOT NULL,
    content         BYTEA       NOT NULL,
    extracted_text  TEXT,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX claim_document_claim_idx ON claim_document (claim_id);

CREATE TABLE extracted_field (
    id          UUID PRIMARY KEY,
    claim_id    UUID          NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    name        TEXT          NOT NULL,
    value       TEXT,
    confidence  NUMERIC(4, 3) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    verified_by TEXT,
    UNIQUE (claim_id, name)
);

CREATE TABLE review_task (
    id              UUID PRIMARY KEY,
    claim_id        UUID        NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    task_type       TEXT        NOT NULL CHECK (task_type IN ('VERIFY_FIELDS', 'ADJUSTER_REVIEW',
                                                              'PAYOUT_PREPARE', 'PAYOUT_RELEASE')),
    candidate_role  TEXT        NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('OPEN', 'COMPLETED')),
    job_key         BIGINT      NOT NULL UNIQUE,
    sla_due_at      TIMESTAMPTZ NOT NULL,
    escalated       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,
    completed_by    TEXT,
    outcome         TEXT,
    -- Outbox for resuming the process: written with the completion, published after commit.
    resume_variables TEXT,
    resumed_at      TIMESTAMPTZ
);

CREATE INDEX review_task_resume_idx ON review_task (completed_at) WHERE status = 'COMPLETED' AND resumed_at IS NULL;

CREATE INDEX review_task_open_idx ON review_task (status, candidate_role);
CREATE INDEX review_task_claim_idx ON review_task (claim_id);

CREATE TABLE payout (
    id               UUID PRIMARY KEY,
    claim_id         UUID           NOT NULL REFERENCES claim (id),
    idempotency_key  TEXT           NOT NULL UNIQUE,
    amount           NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    account          TEXT           NOT NULL,
    status           TEXT           NOT NULL CHECK (status IN ('EXECUTED', 'REVERSED')),
    reference        TEXT           NOT NULL UNIQUE,
    executed_at      TIMESTAMPTZ    NOT NULL,
    reversed_at      TIMESTAMPTZ
);

CREATE TABLE audit_event (
    id           BIGSERIAL PRIMARY KEY,
    claim_id     UUID        NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    occurred_at  TIMESTAMPTZ NOT NULL,
    actor        TEXT        NOT NULL,
    event_type   TEXT        NOT NULL,
    detail       TEXT
);

CREATE INDEX audit_event_claim_idx ON audit_event (claim_id, occurred_at);
