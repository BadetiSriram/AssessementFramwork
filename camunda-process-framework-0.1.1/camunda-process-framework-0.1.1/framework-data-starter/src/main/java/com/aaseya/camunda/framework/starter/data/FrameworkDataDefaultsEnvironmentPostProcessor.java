package com.aaseya.camunda.framework.starter.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Registers a lowest-priority property source that injects opinionated JPA and Flyway
 * defaults for all consuming services built on this framework.
 *
 * <p>This post-processor runs before the application context is refreshed, inserting a
 * {@link MapPropertySource} named {@code frameworkDataDefaults} at the very end of the
 * environment's property source chain (via {@code addLast}). Because Spring resolves
 * properties from highest to lowest precedence, any value set explicitly by the consuming
 * service — whether in {@code application.yml}, {@code application.properties}, system
 * properties, or environment variables — automatically takes priority over the defaults
 * injected here.
 *
 * <p>The following defaults are applied:
 * <ul>
 *   <li>{@code spring.jpa.hibernate.ddl-auto=validate} — schema management is owned by
 *       Flyway; Hibernate must not auto-generate or modify tables.</li>
 *   <li>{@code spring.jpa.open-in-view=false} — OSIV must be disabled to prevent
 *       long-lived database connections across the HTTP request lifecycle.</li>
 *   <li>{@code spring.flyway.baseline-on-migrate=true} — allows Flyway to baseline an
 *       existing schema on first run without failing.</li>
 *   <li>{@code spring.flyway.validate-migration-naming=true} — rejects migration files
 *       whose names do not conform to Flyway's versioned-migration convention.</li>
 * </ul>
 *
 * <p>To override any default, set the property explicitly in the consuming service:
 * <pre>{@code
 * spring:
 *   jpa:
 *     open-in-view: true   # not recommended; overrides framework default
 * }</pre>
 *
 * <p>The registration of this post-processor is driven by
 * {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports},
 * so no additional Spring Boot configuration is required in the consuming service.
 */
public class FrameworkDataDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "frameworkDataDefaults";

    /**
     * Adds the framework data defaults at the lowest precedence in the given environment.
     *
     * @param environment the environment to post-process; never {@code null}
     * @param application the application being started; may be {@code null} in tests
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        Map<String, Object> defaults = Map.of(
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.open-in-view", "false",
                "spring.flyway.baseline-on-migrate", "true",
                "spring.flyway.validate-migration-naming", "true"
        );
        MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, defaults);
        environment.getPropertySources().addLast(propertySource);
    }
}
