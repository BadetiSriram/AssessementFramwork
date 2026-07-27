-- Framework tables shared by every service built on camunda-process-framework.
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
    payload        TEXT         NOT NULL,   -- see comment above re: jsonb
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at  TIMESTAMP
);

-- Index supporting JdbcOutboxRelay.poll():
--   WHERE dispatched_at IS NULL ORDER BY created_at
--
-- Deliberately NOT a partial index. Postgres supports `CREATE INDEX ... WHERE`,
-- but H2 rejects it as a syntax error even under MODE=PostgreSQL, which breaks
-- the `local` profile. The two-column form is portable and still serves the poll
-- query. Production Postgres deployments that want the smaller partial index can
-- swap it in a later migration:
--
--   DROP INDEX idx_process_outbox_undispatched;
--   CREATE INDEX idx_process_outbox_undispatched ON process_outbox (created_at)
--       WHERE dispatched_at IS NULL;
CREATE INDEX idx_process_outbox_undispatched
    ON process_outbox (dispatched_at, created_at);
