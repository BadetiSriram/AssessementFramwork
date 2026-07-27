package com.aaseya.camunda.framework.starter.data;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link FrameworkDataAutoConfiguration} registers the correct beans under
 * the correct conditions.
 *
 * <p>Uses {@link ApplicationContextRunner} for lightweight context construction — no full
 * Spring Boot application, no DataSource, and no Flyway cluster required.
 *
 * <p>Because {@link FrameworkDataAutoConfiguration} is conditional on
 * {@code jakarta.persistence.EntityManager} being on the classpath, and the test scope
 * includes {@code spring-boot-starter-data-jpa} (provided scope in the starter), JPA
 * classes are available during tests through the test classpath.
 */
class FrameworkDataAutoConfigurationBeansTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FrameworkDataAutoConfiguration.class));

    // -------------------------------------------------------------------------
    // AuditColumnListener bean
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@link AuditColumnListener} is registered when JPA is on the classpath
     * (which it is, since the starter's provided-scope dependency is on the test classpath).
     */
    @Test
    void auditColumnListener_registeredWhenJpaOnClasspath() {
        runner.run(context ->
                assertThat(context).hasSingleBean(AuditColumnListener.class)
        );
    }

    /**
     * Verifies that the auto-configuration yields to a user-defined {@link AuditColumnListener}
     * bean when one is present, due to the {@code @ConditionalOnMissingBean} guard.
     */
    @Test
    void auditColumnListener_autoConfigYieldsToUserDefinedBean() {
        AuditColumnListener customListener = new AuditColumnListener();

        runner
                .withBean(AuditColumnListener.class, () -> customListener)
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditColumnListener.class);
                    assertThat(context.getBean(AuditColumnListener.class)).isSameAs(customListener);
                });
    }

    // -------------------------------------------------------------------------
    // FlywayNamingConventionValidator bean
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@link FlywayNamingConventionValidator} is registered by default when
     * Flyway is on the classpath (i.e., when the property is not explicitly set to
     * {@code false}).
     */
    @Test
    void flywayNamingConventionValidator_registeredByDefault_whenFlywayOnClasspath() {
        runner.run(context ->
                assertThat(context).hasSingleBean(FlywayNamingConventionValidator.class)
        );
    }

    /**
     * Verifies that {@link FlywayNamingConventionValidator} is absent when
     * {@code framework.data.flyway.enforce-naming-convention=false}.
     */
    @Test
    void flywayNamingConventionValidator_absentWhenPropertyDisabled() {
        runner
                .withPropertyValues("framework.data.flyway.enforce-naming-convention=false")
                .run(context ->
                        assertThat(context).doesNotHaveBean(FlywayNamingConventionValidator.class)
                );
    }

    /**
     * Verifies that {@link FlywayNamingConventionValidator} is present when
     * {@code framework.data.flyway.enforce-naming-convention=true} is explicitly set.
     */
    @Test
    void flywayNamingConventionValidator_presentWhenPropertyExplicitlyEnabled() {
        runner
                .withPropertyValues("framework.data.flyway.enforce-naming-convention=true")
                .run(context ->
                        assertThat(context).hasSingleBean(FlywayNamingConventionValidator.class)
                );
    }

    // -------------------------------------------------------------------------
    // FrameworkDataProperties binding
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@link FrameworkDataProperties} is registered as a bean by the
     * auto-configuration.
     */
    @Test
    void frameworkDataProperties_registeredWhenJpaOnClasspath() {
        runner.run(context ->
                assertThat(context).hasSingleBean(FrameworkDataProperties.class)
        );
    }
}
