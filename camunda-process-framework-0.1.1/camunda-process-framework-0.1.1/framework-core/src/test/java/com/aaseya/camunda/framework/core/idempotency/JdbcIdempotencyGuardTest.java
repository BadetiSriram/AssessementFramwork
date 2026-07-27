package com.aaseya.camunda.framework.core.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link JdbcIdempotencyGuard} against an H2 in-memory database.
 * Tests the check-then-record flow and ensures duplicate inserts are handled gracefully.
 */
class JdbcIdempotencyGuardTest {

    private JdbcIdempotencyGuard guard;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        // Use H2 compatibility mode; MERGE replaces ON CONFLICT in H2
        ds.setUrl("jdbc:h2:mem:idempotency-test-" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ds.setUsername("sa");
        ds.setPassword("");

        jdbc = new JdbcTemplate(ds);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS worker_execution (
                    business_key VARCHAR NOT NULL,
                    element_id   VARCHAR NOT NULL,
                    completed_at TIMESTAMP DEFAULT current_timestamp,
                    result_hash  VARCHAR NULL,
                    PRIMARY KEY  (business_key, element_id)
                )
                """);

        guard = new JdbcIdempotencyGuard(jdbc);
    }

    @Test
    void check_beforeRecord_returnsFalse() {
        assertThat(guard.check("BK-100", "Task_Validate")).isFalse();
    }

    @Test
    void record_thenCheck_returnsTrue() {
        guard.record("BK-200", "Task_Charge", null);

        assertThat(guard.check("BK-200", "Task_Charge")).isTrue();
    }

    @Test
    void differentElementId_isNotAReplay() {
        guard.record("BK-300", "Task_Validate", null);

        assertThat(guard.check("BK-300", "Task_Charge")).isFalse();
    }

    @Test
    void differentBusinessKey_isNotAReplay() {
        guard.record("BK-400", "Task_Validate", null);

        assertThat(guard.check("BK-999", "Task_Validate")).isFalse();
    }

    @Test
    void duplicateRecord_doesNotThrow() {
        guard.record("BK-500", "Task_Notify", "hash-abc");

        // Second insert on same PK should silently be ignored
        org.assertj.core.api.Assertions.assertThatCode(
                () -> guard.record("BK-500", "Task_Notify", "hash-abc"))
                .doesNotThrowAnyException();

        assertThat(guard.check("BK-500", "Task_Notify")).isTrue();
    }

    @Test
    void record_withResultHash_isStoredAndCheckable() {
        guard.record("BK-600", "Task_Enrich", "sha256:abc123");

        assertThat(guard.check("BK-600", "Task_Enrich")).isTrue();
    }
}
