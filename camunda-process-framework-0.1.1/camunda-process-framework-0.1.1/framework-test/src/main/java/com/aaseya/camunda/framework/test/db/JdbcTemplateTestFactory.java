package com.aaseya.camunda.framework.test.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Factory for lightweight, in-memory {@link JdbcTemplate} instances backed by an H2
 * database configured in PostgreSQL-compatibility mode.
 *
 * <h2>Purpose</h2>
 * <p>Test code that exercises JDBC-based infrastructure (repositories, idempotency guards,
 * outbox relays) often needs a real {@link JdbcTemplate} without a live PostgreSQL server.
 * This factory wires an H2 in-memory DataSource with the minimal configuration required to
 * make H2 behave like PostgreSQL for DDL and DML that the framework generates.</p>
 *
 * <h2>Idempotency</h2>
 * <p>Because H2 in-memory databases use {@code DB_CLOSE_DELAY=-1}, the database remains
 * open for the lifetime of the JVM.  Calling these methods multiple times with the same
 * {@code schemaName} returns connections to the same shared in-memory database — no data
 * is lost between calls.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * JdbcTemplate template = JdbcTemplateTestFactory.h2JdbcTemplate("myservice_test");
 * template.execute("CREATE TABLE IF NOT EXISTS items (id BIGINT PRIMARY KEY)");
 * }</pre>
 */
public final class JdbcTemplateTestFactory {

    /** H2 JDBC driver class name. */
    private static final String H2_DRIVER = "org.h2.Driver";

    /**
     * Prevents instantiation — this class is a static factory only.
     */
    private JdbcTemplateTestFactory() {
        throw new AssertionError("JdbcTemplateTestFactory must not be instantiated");
    }

    /**
     * Creates a {@link DataSource} backed by an H2 in-memory database configured in
     * PostgreSQL-compatibility mode.
     *
     * <p>The connection URL is:
     * {@code jdbc:h2:mem:<schemaName>;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}</p>
     *
     * <p>{@code DB_CLOSE_DELAY=-1} keeps the database alive for the JVM lifetime so
     * that multiple calls with the same {@code schemaName} share the same data.</p>
     *
     * @param schemaName a short, URL-safe name used as the H2 database name (e.g.
     *                   {@code "myservice_test"}); must not contain spaces or special
     *                   characters
     * @return a configured {@link DriverManagerDataSource} pointing to the named H2
     *         in-memory database
     */
    public static DataSource h2InMemoryDataSource(String schemaName) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(H2_DRIVER);
        ds.setUrl("jdbc:h2:mem:" + schemaName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    /**
     * Creates a {@link JdbcTemplate} backed by an H2 in-memory database with
     * PostgreSQL-compatibility mode enabled.
     *
     * <p>Equivalent to wrapping {@link #h2InMemoryDataSource(String)} in a new
     * {@link JdbcTemplate}.</p>
     *
     * @param schemaName the database name; see {@link #h2InMemoryDataSource(String)}
     * @return a configured {@link JdbcTemplate} for the named in-memory database
     */
    public static JdbcTemplate h2JdbcTemplate(String schemaName) {
        return new JdbcTemplate(h2InMemoryDataSource(schemaName));
    }

    /**
     * Creates a {@link JdbcTemplate} and immediately applies the supplied DDL.
     *
     * <p>Useful for test fixtures that need tables created before any test method runs.
     * The DDL is executed once against the named schema; subsequent calls with the same
     * {@code schemaName} reuse the existing database (H2 does not reset it between calls),
     * so {@code CREATE TABLE IF NOT EXISTS} is recommended in the supplied DDL string.</p>
     *
     * @param schemaName the database name; see {@link #h2InMemoryDataSource(String)}
     * @param ddl        a DDL statement or sequence of statements (separated by
     *                   {@code ;}) to execute immediately after obtaining the template;
     *                   each statement is executed via {@link JdbcTemplate#execute(String)}
     *                   after splitting on {@code ";"}
     * @return a configured {@link JdbcTemplate} with the DDL already applied
     */
    public static JdbcTemplate h2JdbcTemplateWithSchema(String schemaName, String ddl) {
        JdbcTemplate template = h2JdbcTemplate(schemaName);
        for (String statement : ddl.split(";")) {
            String trimmed = statement.strip();
            if (!trimmed.isEmpty()) {
                template.execute(trimmed);
            }
        }
        return template;
    }
}
