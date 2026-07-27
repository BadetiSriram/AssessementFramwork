-- Framework tables shared by every service built on camunda-process-framework.
-- Copied verbatim from service-template. Consumer migrations start at V2.
-- payload is TEXT for H2 compatibility; production Postgres deployments may migrate
-- this column to jsonb in a follow-up migration if native JSON operators are needed.

CREATE TABLE worker_execution (
    business_key   VARCHAR(200) NOT NULL,
    element_id     VARCHAR(200) NOT NULL,
    completed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result_hash    VARCHAR(200),
    PRIMARY KEY (business_key, element_id)
);

CREATE TABLE process_outbox (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(200) NOT NULL,
    kind           VARCHAR(20)  NOT NULL,   -- 'START' | 'MESSAGE'
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at  TIMESTAMP
);

-- Two-column (portable) index supporting JdbcOutboxRelay.poll():
--   WHERE dispatched_at IS NULL ORDER BY created_at
CREATE INDEX idx_process_outbox_undispatched
    ON process_outbox (dispatched_at, created_at);
