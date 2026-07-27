package com.aaseya.camunda.framework.core.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC-backed {@link IdempotencyGuard} that persists execution records in the
 * {@code worker_execution} table.  Uses {@code ON CONFLICT DO NOTHING} so concurrent
 * insertions are safe without application-level locking.
 *
 * <p>Expected DDL (Flyway migration in consuming service):
 * <pre>{@code
 * CREATE TABLE worker_execution (
 *   business_key VARCHAR NOT NULL,
 *   element_id   VARCHAR NOT NULL,
 *   completed_at TIMESTAMP DEFAULT current_timestamp,
 *   result_hash  VARCHAR NULL,
 *   PRIMARY KEY (business_key, element_id)
 * );
 * }</pre>
 */
public class JdbcIdempotencyGuard implements IdempotencyGuard {

    private static final String SQL_CHECK =
            "SELECT COUNT(*) FROM worker_execution WHERE business_key = ? AND element_id = ?";

    private static final String SQL_INSERT =
            "INSERT INTO worker_execution(business_key, element_id, result_hash) "
            + "VALUES (?, ?, ?) ON CONFLICT DO NOTHING";

    private final JdbcTemplate jdbc;

    /**
     * Creates the guard backed by the given {@link JdbcTemplate}.
     *
     * @param jdbc Spring JDBC template connected to the service database
     */
    public JdbcIdempotencyGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@inheritDoc} */
    @Override
    public boolean check(String businessKey, String elementId) {
        Integer count = jdbc.queryForObject(SQL_CHECK, Integer.class, businessKey, elementId);
        return count != null && count > 0;
    }

    /** {@inheritDoc} */
    @Override
    public void record(String businessKey, String elementId, String resultHash) {
        jdbc.update(SQL_INSERT, businessKey, elementId, resultHash);
    }
}
