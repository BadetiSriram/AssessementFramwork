package com.aaseya.camunda.framework.starter.data;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the framework data starter.
 *
 * <p>All properties are bound under the {@code framework.data} prefix. Example:
 *
 * <pre>{@code
 * framework:
 *   data:
 *     audit:
 *       enabled: true
 *       created-by-header: X-User-Id
 *     flyway:
 *       enforce-naming-convention: true
 *       expected-locations:
 *         - classpath:db/migration
 * }</pre>
 *
 * <p>Values not explicitly set in the application configuration fall back to the built-in
 * defaults defined in {@link Audit} and {@link Flyway}.
 */
@ConfigurationProperties(prefix = "framework.data")
public class FrameworkDataProperties {

    private Audit audit = new Audit();
    private Flyway flyway = new Flyway();

    /**
     * Returns the audit configuration properties.
     *
     * @return the {@link Audit} settings; never {@code null}
     */
    public Audit getAudit() {
        return audit;
    }

    /**
     * Sets the audit configuration properties.
     *
     * @param audit the audit settings to apply; must not be {@code null}
     */
    public void setAudit(Audit audit) {
        this.audit = audit;
    }

    /**
     * Returns the Flyway configuration properties.
     *
     * @return the {@link Flyway} settings; never {@code null}
     */
    public Flyway getFlyway() {
        return flyway;
    }

    /**
     * Sets the Flyway configuration properties.
     *
     * @param flyway the Flyway settings to apply; must not be {@code null}
     */
    public void setFlyway(Flyway flyway) {
        this.flyway = flyway;
    }

    /**
     * Audit-related configuration for JPA entity listeners.
     *
     * <p>When {@link #enabled} is {@code true}, the {@link AuditColumnListener} will
     * automatically populate {@code createdAt}, {@code updatedAt}, {@code createdBy},
     * and {@code updatedBy} fields on annotated entities.
     */
    public static class Audit {

        /**
         * Whether audit column population is enabled.
         *
         * <p>When {@code true}, the {@link AuditColumnListener} runs on
         * {@code @PrePersist} and {@code @PreUpdate} lifecycle events, stamping
         * timestamp and user-identity fields reflectively on the entity. Set to
         * {@code false} to disable audit population framework-wide.
         */
        private boolean enabled = true;

        /**
         * HTTP request header name from which the current user identity is read.
         *
         * <p>The {@link AuditColumnListener} calls
         * {@code RequestContextHolder.currentRequestAttributes()} and reads this
         * header to stamp {@code createdBy} and {@code updatedBy} on entities.
         * If the header is absent or the listener is running outside a request
         * context (e.g., in a background worker thread), the fields are left
         * {@code null}.
         */
        private String createdByHeader = "X-User-Id";

        /**
         * Creates an {@code Audit} instance with all default values applied.
         */
        public Audit() {
        }

        /**
         * Returns whether audit column population is enabled.
         *
         * @return {@code true} if audit stamping is active
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether audit column population is enabled.
         *
         * @param enabled {@code true} to enable audit stamping
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the HTTP header name used to resolve the current user identity.
         *
         * @return the header name; never {@code null}
         */
        public String getCreatedByHeader() {
            return createdByHeader;
        }

        /**
         * Sets the HTTP header name used to resolve the current user identity.
         *
         * @param createdByHeader the header name; must not be {@code null}
         */
        public void setCreatedByHeader(String createdByHeader) {
            this.createdByHeader = createdByHeader;
        }
    }

    /**
     * Flyway-related configuration for migration naming validation.
     *
     * <p>When {@link #enforceNamingConvention} is {@code true}, the
     * {@link FlywayNamingConventionValidator} callback runs before each migration and
     * rejects any SQL file whose name does not match the framework's naming pattern.
     */
    public static class Flyway {

        /**
         * Whether to enforce the framework naming convention on Flyway migration files.
         *
         * <p>When {@code true}, every versioned migration must match
         * {@code ^V\d+(_\d+)*__[a-z0-9_]+\.sql$}. A migration file with a name
         * that violates this pattern causes a {@code FlywayException} at startup,
         * failing fast rather than silently applying a mis-named migration.
         */
        private boolean enforceNamingConvention = true;

        /**
         * Expected Flyway migration source locations.
         *
         * <p>This value is informational metadata exposed to the consuming service
         * configuration. Flyway itself is configured via {@code spring.flyway.locations}.
         */
        private List<String> expectedLocations = new ArrayList<>(List.of("classpath:db/migration"));

        /**
         * Creates a {@code Flyway} instance with all default values applied.
         */
        public Flyway() {
        }

        /**
         * Returns whether the framework naming convention is enforced on migration files.
         *
         * @return {@code true} if naming validation is active
         */
        public boolean isEnforceNamingConvention() {
            return enforceNamingConvention;
        }

        /**
         * Sets whether the framework naming convention is enforced on migration files.
         *
         * @param enforceNamingConvention {@code true} to activate naming validation
         */
        public void setEnforceNamingConvention(boolean enforceNamingConvention) {
            this.enforceNamingConvention = enforceNamingConvention;
        }

        /**
         * Returns the expected Flyway migration source locations.
         *
         * @return a mutable list of location strings; never {@code null}
         */
        public List<String> getExpectedLocations() {
            return expectedLocations;
        }

        /**
         * Sets the expected Flyway migration source locations.
         *
         * @param expectedLocations a list of location strings; must not be {@code null}
         */
        public void setExpectedLocations(List<String> expectedLocations) {
            this.expectedLocations = expectedLocations;
        }
    }
}
