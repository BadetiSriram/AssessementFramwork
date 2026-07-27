package com.aaseya.camunda.framework.starter.data;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration entry point for the data framework starter.
 *
 * <p>This class is activated automatically when {@link EntityManager} is present on the
 * classpath — that is, when the consuming service has opted in to
 * {@code spring-boot-starter-data-jpa}. It registers {@link FrameworkDataProperties} so
 * that the {@code framework.data.*} property namespace is available for injection, and
 * wires the audit and Flyway validation infrastructure beans.
 *
 * <p>Every bean is guarded by {@code @ConditionalOnMissingBean} (where applicable) so
 * that consuming services can override any single bean without disabling the rest.
 *
 * <p>The companion {@link FrameworkDataDefaultsEnvironmentPostProcessor} (registered
 * separately via
 * {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports})
 * injects lowest-priority JPA and Flyway defaults before this auto-configuration is
 * evaluated, so services work with safe schema-management settings out of the box.
 */
@AutoConfiguration
@ConditionalOnClass(EntityManager.class)
@EnableConfigurationProperties(FrameworkDataProperties.class)
public class FrameworkDataAutoConfiguration {

    /**
     * Registers the {@link AuditColumnListener} as a Spring-managed bean.
     *
     * <p>Exposing the listener as a Spring bean allows frameworks that integrate JPA
     * entity listeners with the application context (e.g., Spring Data JPA's
     * {@code AuditingEntityListener} integration) to discover and configure it. The
     * {@code @ConditionalOnMissingBean} guard allows consuming services to supply a
     * custom {@code AuditColumnListener} subclass without conflict.
     *
     * @return a new {@link AuditColumnListener} instance
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditColumnListener frameworkAuditColumnListener() {
        return new AuditColumnListener();
    }

    /**
     * Registers the {@link FlywayNamingConventionValidator} when Flyway is present on the
     * classpath and the naming convention enforcement property is enabled (the default).
     *
     * <p>Spring Boot's Flyway auto-configuration picks up all {@code Callback} beans from
     * the application context and registers them with the {@code Flyway} instance
     * automatically, so no additional wiring is required in the consuming service.
     *
     * <p>To disable naming validation:
     * <pre>{@code
     * framework:
     *   data:
     *     flyway:
     *       enforce-naming-convention: false
     * }</pre>
     *
     * @return a new {@link FlywayNamingConventionValidator} instance
     */
    @Bean
    @ConditionalOnClass(name = "org.flywaydb.core.Flyway")
    @ConditionalOnProperty(
            prefix = "framework.data.flyway",
            name = "enforce-naming-convention",
            havingValue = "true",
            matchIfMissing = true
    )
    public FlywayNamingConventionValidator frameworkFlywayNamingConventionValidator() {
        return new FlywayNamingConventionValidator();
    }
}
