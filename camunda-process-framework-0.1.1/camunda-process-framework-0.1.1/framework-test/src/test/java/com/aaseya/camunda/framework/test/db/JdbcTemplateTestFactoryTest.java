package com.aaseya.camunda.framework.test.db;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Self-tests for {@link JdbcTemplateTestFactory}.
 *
 * <p>Verifies that the factory correctly creates H2 in-memory {@link JdbcTemplate}
 * instances, that basic SQL executes against them, and that the idempotency guarantee
 * (same schema name reuses the same in-memory database) holds.</p>
 */
class JdbcTemplateTestFactoryTest {

    private static final String SCHEMA_NAME = "framework_test_db";

    @Test
    void h2InMemoryDataSource_returnsNonNullDataSource() {
        DataSource ds = JdbcTemplateTestFactory.h2InMemoryDataSource(SCHEMA_NAME);
        assertThat(ds).isNotNull();
    }

    @Test
    void h2InMemoryDataSource_canObtainConnection() throws SQLException {
        DataSource ds = JdbcTemplateTestFactory.h2InMemoryDataSource(SCHEMA_NAME);
        try (Connection conn = ds.getConnection()) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }
    }

    @Test
    void h2JdbcTemplate_selectOneReturnsOne() {
        JdbcTemplate template = JdbcTemplateTestFactory.h2JdbcTemplate(SCHEMA_NAME);
        Integer result = template.queryForObject("SELECT 1", Integer.class);
        assertThat(result).isEqualTo(1);
    }

    @Test
    void h2JdbcTemplate_isIdempotent_sameSchemaReusesSameDatabase() {
        JdbcTemplate first = JdbcTemplateTestFactory.h2JdbcTemplate(SCHEMA_NAME + "_idempotent");
        first.execute("CREATE TABLE IF NOT EXISTS idempotency_check (id INT PRIMARY KEY)");
        first.execute("INSERT INTO idempotency_check VALUES (42)");

        // Second call with the same name — must see the row inserted by the first call
        JdbcTemplate second = JdbcTemplateTestFactory.h2JdbcTemplate(SCHEMA_NAME + "_idempotent");
        Integer count = second.queryForObject(
                "SELECT COUNT(*) FROM idempotency_check WHERE id = 42", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void h2JdbcTemplateWithSchema_executesDdlBeforeReturning() {
        String ddl = "CREATE TABLE IF NOT EXISTS test_items (id BIGINT PRIMARY KEY, name VARCHAR(100))";
        JdbcTemplate template = JdbcTemplateTestFactory.h2JdbcTemplateWithSchema(
                SCHEMA_NAME + "_schema", ddl);

        // If the DDL was applied, this insert must succeed without an exception
        assertThatCode(() ->
                template.execute("INSERT INTO test_items (id, name) VALUES (1, 'framework-test')")
        ).doesNotThrowAnyException();
    }

    @Test
    void h2JdbcTemplateWithSchema_multiStatementDdlIsApplied() {
        String ddl = "CREATE TABLE IF NOT EXISTS multi_a (id INT PRIMARY KEY);"
                + "CREATE TABLE IF NOT EXISTS multi_b (id INT PRIMARY KEY)";
        JdbcTemplate template = JdbcTemplateTestFactory.h2JdbcTemplateWithSchema(
                SCHEMA_NAME + "_multi", ddl);

        // Both tables should exist; querying them should not throw
        assertThatCode(() -> template.execute("SELECT * FROM multi_a")).doesNotThrowAnyException();
        assertThatCode(() -> template.execute("SELECT * FROM multi_b")).doesNotThrowAnyException();
    }
}
