package com.aaseya.camunda.framework.starter.data;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Flyway {@link Callback} that enforces the framework's SQL migration naming convention
 * before each migration run.
 *
 * <p>Every versioned migration script must match the pattern:
 * <pre>{@code
 * ^V\d+(_\d+)*__[a-z0-9_]+\.sql$
 * }</pre>
 *
 * <p>Examples of valid names:
 * <ul>
 *   <li>{@code V1__init.sql}</li>
 *   <li>{@code V2_3__add_index.sql}</li>
 *   <li>{@code V10_1_2__create_audit_table.sql}</li>
 * </ul>
 *
 * <p>Examples of invalid names (rejected at startup):
 * <ul>
 *   <li>{@code V1__Init.sql} — capital letter in description</li>
 *   <li>{@code V1__add index.sql} — space in description</li>
 *   <li>{@code V1__addUserTable.sql} — camelCase in description</li>
 *   <li>{@code v1__init.sql} — lowercase version prefix</li>
 * </ul>
 *
 * <p>When a violation is detected, an ERROR is logged listing all offending filenames and
 * a {@link FlywayException} is thrown to halt application startup immediately. This fails
 * fast rather than allowing a mis-named script to be silently applied.
 *
 * <p>This validator runs only on the {@link Event#BEFORE_MIGRATE} event. Repeatable
 * migrations (prefixed with {@code R__}) and any file not starting with {@code V} are
 * not subject to this pattern and are skipped.
 *
 * <p>Registration is handled by {@link FrameworkDataAutoConfiguration}; consuming services
 * do not need to register this bean manually. The bean can be suppressed by setting
 * {@code framework.data.flyway.enforce-naming-convention=false}.
 */
public class FlywayNamingConventionValidator implements Callback {

    private static final Logger log = LoggerFactory.getLogger(FlywayNamingConventionValidator.class);

    /**
     * Pattern that every versioned migration filename must match.
     *
     * <p>Breakdown:
     * <ul>
     *   <li>{@code ^V} — uppercase V prefix</li>
     *   <li>{@code \d+(_\d+)*} — version number, optionally dotted (using underscore)</li>
     *   <li>{@code __} — double underscore separator</li>
     *   <li>{@code [a-z0-9_]+} — lowercase description with digits and underscores only</li>
     *   <li>{@code \.sql$} — must end with {@code .sql}</li>
     * </ul>
     */
    static final Pattern VALID_MIGRATION_NAME =
            Pattern.compile("^V\\d+(_\\d+)*__[a-z0-9_]+\\.sql$");

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code true} only for the {@link Event#BEFORE_MIGRATE} event.
     *
     * @param event   the Flyway lifecycle event
     * @param context the current migration context
     * @return {@code true} if this callback handles the given event
     */
    @Override
    public boolean supports(Event event, Context context) {
        return Event.BEFORE_MIGRATE.equals(event);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Scans migration resources discovered by the Flyway configuration's resource
     * provider for naming violations. If any violation is found, logs all offending names
     * at ERROR level and throws a {@link FlywayException} to abort the migration.
     *
     * @param event   the Flyway lifecycle event ({@code BEFORE_MIGRATE})
     * @param context the current migration context
     */
    @Override
    public void handle(Event event, Context context) {
        Configuration config = context.getConfiguration();
        List<String> filenames = collectMigrationFilenames(config);
        validateFilenames(filenames);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code false} — this callback does not require a transaction.
     *
     * @param event   the Flyway lifecycle event
     * @param context the current migration context
     * @return {@code false} always
     */
    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false;
    }

    /** Callback name used for Flyway logging and diagnostics. */
    @Override
    public String getCallbackName() {
        return "FrameworkNamingConvention";
    }

    /**
     * Collects migration filenames from the Flyway configuration's resource provider.
     *
     * <p>Uses {@code Configuration.getResourceProvider()} when available; falls back to
     * returning an empty list when the provider is not configured (e.g., during unit tests
     * where only the validator logic is exercised directly via
     * {@link #validateFilenames(List)}).
     *
     * @param config the active Flyway configuration; never {@code null}
     * @return a list of migration script filenames (not full paths); never {@code null}
     */
    List<String> collectMigrationFilenames(Configuration config) {
        List<String> names = new ArrayList<>();
        try {
            var resourceProvider = config.getResourceProvider();
            if (resourceProvider == null) {
                return names;
            }
            String[] sqlMigrationSuffixes = config.getSqlMigrationSuffixes();
            if (sqlMigrationSuffixes == null || sqlMigrationSuffixes.length == 0) {
                sqlMigrationSuffixes = new String[]{".sql"};
            }
            for (String suffix : sqlMigrationSuffixes) {
                Collection<LoadableResource> resources =
                        resourceProvider.getResources("V", new String[]{suffix});
                for (LoadableResource resource : resources) {
                    names.add(extractFilename(resource.getFilename()));
                }
            }
        } catch (Exception ex) {
            log.debug("Could not collect migration filenames from resource provider: {}",
                    ex.getMessage(), ex);
        }
        return names;
    }

    /**
     * Validates a list of migration filenames against the naming convention pattern.
     *
     * <p>This method is package-private to allow direct testing of validation logic
     * without requiring a live Flyway context. Files not starting with {@code V} are
     * skipped; only versioned migration filenames are subject to the pattern check.
     *
     * @param filenames the list of migration script filenames to validate; never {@code null}
     * @throws FlywayException if one or more filenames violate the naming convention
     */
    void validateFilenames(List<String> filenames) {
        List<String> violations = new ArrayList<>();
        for (String filename : filenames) {
            if (filename == null || filename.isEmpty()) {
                continue;
            }
            // Case-insensitive V-prefix filter so typos like "v1__init.sql" reach the strict
            // regex (which requires uppercase V) and get reported as violations. R__ and U__
            // migrations are legitimately non-V — skip them.
            if (Character.toUpperCase(filename.charAt(0)) != 'V') {
                continue;
            }
            if (!VALID_MIGRATION_NAME.matcher(filename).matches()) {
                violations.add(filename);
            }
        }
        if (!violations.isEmpty()) {
            log.error("Flyway migration naming convention violations detected. "
                    + "All versioned migrations must match ^V\\d+(_\\d+)*__[a-z0-9_]+\\.sql$. "
                    + "Offending files: {}", violations);
            throw new FlywayException(
                    "Migration naming convention violations: " + violations
                    + ". Pattern required: ^V\\d+(_\\d+)*__[a-z0-9_]+\\.sql$");
        }
    }

    /**
     * Extracts the filename component from a migration script path.
     *
     * <p>If the script contains a path separator, only the portion after the last
     * separator is returned. Otherwise the full script string is returned.
     *
     * @param script the migration script identifier; never {@code null}
     * @return the filename without any leading path components
     */
    private String extractFilename(String script) {
        int lastSlash = Math.max(script.lastIndexOf('/'), script.lastIndexOf('\\'));
        return (lastSlash >= 0) ? script.substring(lastSlash + 1) : script;
    }
}
