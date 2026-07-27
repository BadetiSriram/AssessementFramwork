package com.aaseya.camunda.framework.starter.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory and helper class that produces a {@link JwtAuthenticationConverter} configured
 * to extract roles from a configurable dot-path inside the JWT claims tree.
 *
 * <p>The canonical use-case is Keycloak's {@code realm_access.roles} structure, but the
 * path is driven entirely by {@link FrameworkSecurityProperties.Jwt#getRolesClaim()} so
 * no realm name, client ID, or issuer URI is ever hardcoded here.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @Bean
 * public SecurityFilterChain securityFilterChain(
 *         HttpSecurity http,
 *         JwtAuthenticationConverter frameworkJwtAuthenticationConverter) throws Exception {
 *     http
 *         .oauth2ResourceServer(oauth -> oauth
 *             .jwt(j -> j.jwtAuthenticationConverter(frameworkJwtAuthenticationConverter)));
 *     return http.build();
 * }
 * }</pre>
 */
public final class JwtRealmRolesAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate;

    /**
     * Creates an instance wrapping the given pre-configured delegate.
     *
     * @param delegate fully configured {@link JwtAuthenticationConverter}
     */
    private JwtRealmRolesAuthenticationConverter(JwtAuthenticationConverter delegate) {
        this.delegate = delegate;
    }

    /**
     * Factory method — builds a {@link JwtAuthenticationConverter} that reads roles from
     * the path expressed by {@link FrameworkSecurityProperties.Jwt#getRolesClaim()} and
     * prepends {@link FrameworkSecurityProperties.Jwt#getRolePrefix()} to each role.
     *
     * @param props JWT properties sourced from {@code framework.security.jwt.*}
     * @return a fully configured {@link JwtAuthenticationConverter}
     */
    public static JwtAuthenticationConverter newConverter(FrameworkSecurityProperties.Jwt props) {
        String rolesClaim = props.getRolesClaim();
        String rolePrefix = props.getRolePrefix();

        Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter =
                jwt -> extractAuthorities(jwt, rolesClaim, rolePrefix);

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * Resolves the dot-delimited {@code rolesClaim} path against the JWT claims map,
     * then converts each role string into a {@link SimpleGrantedAuthority}.
     *
     * <p>Supports paths of one, two, or three segments.  If any intermediate segment is
     * absent or not a {@link Map}, an empty collection is returned rather than throwing.
     *
     * @param jwt        the JWT to inspect
     * @param rolesClaim dot-delimited path, e.g. {@code realm_access.roles} or {@code roles}
     * @param rolePrefix prefix to prepend to each uppercased role string
     * @return authorities extracted from the JWT; never {@code null}
     */
    @SuppressWarnings("unchecked")
    static Collection<GrantedAuthority> extractAuthorities(Jwt jwt,
                                                           String rolesClaim,
                                                           String rolePrefix) {
        if (rolesClaim == null || rolesClaim.isBlank()) {
            return Collections.emptyList();
        }

        String[] segments = rolesClaim.split("\\.", -1);
        Object current = jwt.getClaims();

        for (int i = 0; i < segments.length - 1; i++) {
            if (!(current instanceof Map)) {
                return Collections.emptyList();
            }
            current = ((Map<String, Object>) current).get(segments[i]);
            if (current == null) {
                return Collections.emptyList();
            }
        }

        // Final segment must resolve to a List<String>
        String lastSegment = segments[segments.length - 1];
        Object rolesObj;
        if (current instanceof Map) {
            rolesObj = ((Map<String, Object>) current).get(lastSegment);
        } else {
            return Collections.emptyList();
        }

        if (!(rolesObj instanceof List)) {
            return Collections.emptyList();
        }

        List<?> roles = (List<?>) rolesObj;
        String prefix = (rolePrefix == null) ? "" : rolePrefix;

        return roles.stream()
                .filter(r -> r instanceof String)
                .map(r -> (String) r)
                .map(r -> prefix + r.toUpperCase())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    /**
     * Delegates to the wrapped {@link JwtAuthenticationConverter}.
     *
     * @param jwt the JWT token
     * @return the resulting {@link AbstractAuthenticationToken}
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return delegate.convert(jwt);
    }
}
