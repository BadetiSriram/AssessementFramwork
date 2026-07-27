package com.aaseya.camunda.framework.starter.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Auto-configuration for the framework security starter.
 *
 * <p>Activated when {@code org.springframework.security.oauth2.jwt.Jwt} is on the
 * classpath (i.e., when {@code spring-boot-starter-oauth2-resource-server} is a
 * dependency).  Registers two optional beans:
 *
 * <ol>
 *   <li>{@link JwtAuthenticationConverter} — reads realm roles from the JWT claim path
 *       configured at {@code framework.security.jwt.rolesClaim} (default:
 *       {@code realm_access.roles}) and prefixes them with
 *       {@code framework.security.jwt.rolePrefix} (default: {@code ROLE_}).</li>
 *   <li>{@link CorsConfigurationSource} — assembled from
 *       {@code framework.security.cors.*} and registered only when
 *       {@code framework.security.cors.enabled=true}.</li>
 * </ol>
 *
 * <p>No default {@link org.springframework.security.web.SecurityFilterChain} bean is
 * provided — that decision is intentionally left to the consuming service.  A minimal
 * wiring example:
 *
 * <pre>{@code
 * @Bean
 * public SecurityFilterChain securityFilterChain(
 *         HttpSecurity http,
 *         JwtAuthenticationConverter frameworkJwtAuthenticationConverter,
 *         CorsConfigurationSource frameworkCorsConfigurationSource) throws Exception {
 *     http
 *         .cors(cors -> cors.configurationSource(frameworkCorsConfigurationSource))
 *         .oauth2ResourceServer(oauth -> oauth
 *             .jwt(j -> j.jwtAuthenticationConverter(frameworkJwtAuthenticationConverter)));
 *     return http.build();
 * }
 * }</pre>
 *
 * <p>When {@code framework.security.cors.enabled} is {@code false} (the default),
 * {@code frameworkCorsConfigurationSource} will not exist; omit the
 * {@code CorsConfigurationSource} parameter (or make it {@code @Autowired(required=false)})
 * in that case.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.security.oauth2.jwt.Jwt")
@EnableConfigurationProperties(FrameworkSecurityProperties.class)
public class FrameworkSecurityAutoConfiguration {

    /**
     * Provides a {@link JwtAuthenticationConverter} that extracts realm roles from the
     * JWT using the path and prefix configured under {@code framework.security.jwt.*}.
     *
     * <p>Back-off: if the application registers its own {@link JwtAuthenticationConverter}
     * bean, this one is skipped entirely.
     *
     * @param props security properties
     * @return configured converter
     */
    @Bean
    @ConditionalOnMissingBean(JwtAuthenticationConverter.class)
    public JwtAuthenticationConverter frameworkJwtAuthenticationConverter(
            FrameworkSecurityProperties props) {
        return JwtRealmRolesAuthenticationConverter.newConverter(props.getJwt());
    }

    /**
     * Provides a {@link CorsConfigurationSource} mapped to {@code /**} and configured
     * from {@code framework.security.cors.*}.
     *
     * <p>This bean is registered only when {@code framework.security.cors.enabled=true}.
     * Back-off: if the application registers its own {@link CorsConfigurationSource} bean,
     * this one is skipped entirely.
     *
     * <p>Wiring the source into the security filter chain remains the consuming service's
     * responsibility (see class-level Javadoc for an example).
     *
     * @param props security properties
     * @return configured CORS configuration source
     */
    @Bean
    @ConditionalOnMissingBean(CorsConfigurationSource.class)
    @ConditionalOnProperty(prefix = "framework.security.cors", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "jakarta.servlet.ServletRequest")
    public CorsConfigurationSource frameworkCorsConfigurationSource(
            FrameworkSecurityProperties props) {
        FrameworkSecurityProperties.Cors corsProps = props.getCors();

        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = corsProps.getAllowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            config.setAllowedOrigins(origins);
        }

        List<String> methods = corsProps.getAllowedMethods();
        if (methods != null && !methods.isEmpty()) {
            config.setAllowedMethods(methods);
        }

        List<String> headers = corsProps.getAllowedHeaders();
        if (headers != null && !headers.isEmpty()) {
            config.setAllowedHeaders(headers);
        }

        config.setAllowCredentials(corsProps.isAllowCredentials());

        if (corsProps.getMaxAge() != null) {
            config.setMaxAge(corsProps.getMaxAge().getSeconds());
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
