package com.aaseya.camunda.framework.starter.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtRealmRolesAuthenticationConverter}.
 *
 * <p>Each test builds a {@link Jwt} via the Nimbus-compatible
 * {@link Jwt#withTokenValue(String)} builder to avoid requiring a real token.
 */
class JwtRealmRolesAuthenticationConverterTest {

    // -------------------------------------------------------------------------
    // (a) Keycloak-shape JWT: realm_access.roles -> ROLE_ADMIN, ROLE_USER
    // -------------------------------------------------------------------------

    @Test
    void keycloakRealmAccessRoles_mapsToRoleAuthorities() {
        Jwt jwt = buildJwtWithClaims(Map.of(
                "realm_access", Map.of("roles", List.of("admin", "user"))
        ));

        Collection<GrantedAuthority> authorities =
                JwtRealmRolesAuthenticationConverter.extractAuthorities(
                        jwt, "realm_access.roles", "ROLE_");

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    // -------------------------------------------------------------------------
    // (b) Missing claim path -> empty collection, no exception
    // -------------------------------------------------------------------------

    @Test
    void missingClaimPath_returnsEmptyCollection() {
        Jwt jwt = buildJwtWithClaims(Map.of("sub", "user-id-123"));

        Collection<GrantedAuthority> authorities =
                JwtRealmRolesAuthenticationConverter.extractAuthorities(
                        jwt, "realm_access.roles", "ROLE_");

        assertThat(authorities).isEmpty();
    }

    @Test
    void partialClaimPath_returnsEmptyCollection() {
        // realm_access exists but has no "roles" key
        Jwt jwt = buildJwtWithClaims(Map.of(
                "realm_access", Map.of("other", List.of("value"))
        ));

        Collection<GrantedAuthority> authorities =
                JwtRealmRolesAuthenticationConverter.extractAuthorities(
                        jwt, "realm_access.roles", "ROLE_");

        assertThat(authorities).isEmpty();
    }

    // -------------------------------------------------------------------------
    // (c) Top-level "roles" claim -> works correctly
    // -------------------------------------------------------------------------

    @Test
    void topLevelRolesClaim_mapsToRoleAuthorities() {
        Jwt jwt = buildJwtWithClaims(Map.of(
                "roles", List.of("manager", "viewer")
        ));

        Collection<GrantedAuthority> authorities =
                JwtRealmRolesAuthenticationConverter.extractAuthorities(
                        jwt, "roles", "ROLE_");

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_MANAGER", "ROLE_VIEWER");
    }

    // -------------------------------------------------------------------------
    // (d) Custom rolePrefix="APP_" is respected
    // -------------------------------------------------------------------------

    @Test
    void customRolePrefix_isApplied() {
        Jwt jwt = buildJwtWithClaims(Map.of(
                "realm_access", Map.of("roles", List.of("admin"))
        ));

        Collection<GrantedAuthority> authorities =
                JwtRealmRolesAuthenticationConverter.extractAuthorities(
                        jwt, "realm_access.roles", "APP_");

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("APP_ADMIN");
    }

    // -------------------------------------------------------------------------
    // Factory method integration check
    // -------------------------------------------------------------------------

    @Test
    void newConverter_returnsConfiguredJwtAuthenticationConverter() {
        FrameworkSecurityProperties.Jwt props = new FrameworkSecurityProperties.Jwt();
        props.setRolesClaim("realm_access.roles");
        props.setRolePrefix("ROLE_");

        var converter = JwtRealmRolesAuthenticationConverter.newConverter(props);
        assertThat(converter).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal {@link Jwt} carrying the supplied extra claims.
     * The token value is a placeholder — it is never decoded in unit tests.
     */
    private Jwt buildJwtWithClaims(Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject("test-subject");

        extraClaims.forEach(builder::claim);

        return builder.build();
    }
}
