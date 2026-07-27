/**
 * Auto-configuration and supporting classes for JWT-based OAuth2 resource-server security
 * in the Camunda process framework.  The central entry point is
 * {@link com.aaseya.camunda.framework.starter.security.FrameworkSecurityAutoConfiguration},
 * which registers a {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter}
 * that extracts Keycloak {@code realm_access.roles} (or a configurable dot-path) from the
 * JWT claims tree and maps them to Spring Security {@code GrantedAuthority} instances, as
 * well as an optional {@link org.springframework.web.cors.CorsConfigurationSource} bean
 * (activated by {@code framework.security.cors.enabled=true}).  All tuneable parameters
 * are exposed under the {@code framework.security.*} property namespace via
 * {@link com.aaseya.camunda.framework.starter.security.FrameworkSecurityProperties}.
 */
package com.aaseya.camunda.framework.starter.security;
