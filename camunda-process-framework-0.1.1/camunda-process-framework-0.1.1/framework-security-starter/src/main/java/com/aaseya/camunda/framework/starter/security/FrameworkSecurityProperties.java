package com.aaseya.camunda.framework.starter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration properties for the framework security auto-configuration.
 *
 * <p>All properties are bound under the {@code framework.security} prefix.
 * Two nested namespaces are supported:
 *
 * <ul>
 *   <li>{@code framework.security.cors.*} — CORS policy applied globally to {@code /**}
 *       when {@code framework.security.cors.enabled=true}.</li>
 *   <li>{@code framework.security.jwt.*} — JWT roles extraction.  The default
 *       {@code rolesClaim=realm_access.roles} matches the Keycloak realm-role structure
 *       where the token payload contains a nested object
 *       {@code "realm_access": {"roles": ["admin","user"]}}.
 *       Set {@code rolesClaim=roles} to use a flat top-level array instead.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "framework.security")
public class FrameworkSecurityProperties {

    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();

    /** Returns the CORS configuration block. */
    public Cors getCors() {
        return cors;
    }

    /** Sets the CORS configuration block. */
    public void setCors(Cors cors) {
        this.cors = cors;
    }

    /** Returns the JWT configuration block. */
    public Jwt getJwt() {
        return jwt;
    }

    /** Sets the JWT configuration block. */
    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    // -------------------------------------------------------------------------
    // Nested: Cors
    // -------------------------------------------------------------------------

    /**
     * CORS policy properties.  The CORS filter bean is only registered when
     * {@code framework.security.cors.enabled=true}.
     */
    public static class Cors {

        /** Whether CORS support is enabled. Defaults to {@code false}. */
        private boolean enabled = false;

        /** Allowed origins (e.g. {@code https://app.example.com}). Empty by default. */
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * HTTP methods to allow.
         * Defaults to {@code GET, POST, PUT, DELETE, OPTIONS}.
         */
        private List<String> allowedMethods =
                new ArrayList<>(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        /**
         * Request headers to allow.
         * Defaults to {@code ["*"]} (all headers).
         */
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        /** Whether credentials (cookies, auth headers) may be sent. Defaults to {@code false}. */
        private boolean allowCredentials = false;

        /**
         * How long a pre-flight response may be cached.
         * Defaults to {@code PT1H} (one hour).
         */
        private Duration maxAge = Duration.ofHours(1);

        /** Public no-arg constructor. */
        public Cors() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public Duration getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(Duration maxAge) {
            this.maxAge = maxAge;
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Jwt
    // -------------------------------------------------------------------------

    /**
     * JWT roles-extraction properties.
     *
     * <p>Keycloak embeds realm roles at the path {@code realm_access.roles} inside the
     * JWT payload, producing a nested structure such as:
     * <pre>{@code
     * {
     *   "realm_access": {
     *     "roles": ["admin", "user"]
     *   }
     * }
     * }</pre>
     *
     * <p>Set {@code framework.security.jwt.rolesClaim=realm_access.roles} (the default)
     * to follow this Keycloak convention.  Set it to {@code roles} (a single segment)
     * when your identity provider places roles at the top level of the token.
     *
     * <p>The extracted role strings are uppercased and prefixed with {@code rolePrefix}
     * before being wrapped in {@link org.springframework.security.core.authority.SimpleGrantedAuthority}.
     */
    public static class Jwt {

        /**
         * Dot-delimited path into the JWT claim tree that yields the roles list.
         *
         * <p>For Keycloak realm roles use {@code realm_access.roles} (default).
         * For a flat top-level {@code roles} array use {@code roles}.
         * Paths of up to three segments are resolved.
         */
        private String rolesClaim = "realm_access.roles";

        /**
         * Prefix prepended to each extracted role string.
         * Spring Security conventions require {@code ROLE_} (the default).
         */
        private String rolePrefix = "ROLE_";

        /** Public no-arg constructor. */
        public Jwt() {
        }

        public String getRolesClaim() {
            return rolesClaim;
        }

        public void setRolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
        }

        public String getRolePrefix() {
            return rolePrefix;
        }

        public void setRolePrefix(String rolePrefix) {
            this.rolePrefix = rolePrefix;
        }
    }
}
