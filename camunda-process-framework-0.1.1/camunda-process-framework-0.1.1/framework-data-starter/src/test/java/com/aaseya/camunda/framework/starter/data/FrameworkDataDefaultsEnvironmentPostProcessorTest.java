package com.aaseya.camunda.framework.starter.data;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the behaviour of {@link FrameworkDataDefaultsEnvironmentPostProcessor}.
 *
 * <p>The post-processor inserts opinionated JPA and Flyway defaults at the lowest
 * precedence in the environment. These tests confirm:
 * <ol>
 *   <li>Each default property is present when not overridden by the application.</li>
 *   <li>An explicit application-level value takes precedence over the injected default.</li>
 * </ol>
 *
 * <p>Tests use {@link ApplicationContextRunner} for lightweight context construction
 * without starting a full Spring Boot application.
 */
class FrameworkDataDefaultsEnvironmentPostProcessorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(applicationContext -> {
                FrameworkDataDefaultsEnvironmentPostProcessor postProcessor =
                        new FrameworkDataDefaultsEnvironmentPostProcessor();
                postProcessor.postProcessEnvironment(
                        applicationContext.getEnvironment(),
                        null
                );
            });

    /**
     * Verifies that {@code spring.jpa.hibernate.ddl-auto} defaults to {@code validate}.
     */
    @Test
    void ddlAuto_defaultsToValidate_whenNotExplicitlySet() {
        contextRunner.run(context -> {
            String value = context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto");
            assertThat(value).isEqualTo("validate");
        });
    }

    /**
     * Verifies that {@code spring.jpa.open-in-view} defaults to {@code false}.
     */
    @Test
    void openInView_defaultsToFalse_whenNotExplicitlySet() {
        contextRunner.run(context -> {
            String value = context.getEnvironment().getProperty("spring.jpa.open-in-view");
            assertThat(value).isEqualTo("false");
        });
    }

    /**
     * Verifies that {@code spring.flyway.baseline-on-migrate} defaults to {@code true}.
     */
    @Test
    void flywayBaselineOnMigrate_defaultsToTrue_whenNotExplicitlySet() {
        contextRunner.run(context -> {
            String value = context.getEnvironment().getProperty("spring.flyway.baseline-on-migrate");
            assertThat(value).isEqualTo("true");
        });
    }

    /**
     * Verifies that {@code spring.flyway.validate-migration-naming} defaults to {@code true}.
     */
    @Test
    void flywayValidateMigrationNaming_defaultsToTrue_whenNotExplicitlySet() {
        contextRunner.run(context -> {
            String value = context.getEnvironment().getProperty("spring.flyway.validate-migration-naming");
            assertThat(value).isEqualTo("true");
        });
    }

    /**
     * Verifies that an explicit application-level {@code spring.jpa.hibernate.ddl-auto}
     * property wins over the {@code validate} default injected by the post-processor.
     */
    @Test
    void ddlAuto_userOverrideWins_whenExplicitlySet() {
        contextRunner
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none")
                .run(context -> {
                    String value = context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto");
                    assertThat(value).isEqualTo("none");
                });
    }

    /**
     * Verifies that an explicit application-level {@code spring.jpa.open-in-view} property
     * wins over the {@code false} default injected by the post-processor.
     */
    @Test
    void openInView_userOverrideWins_whenExplicitlySet() {
        contextRunner
                .withPropertyValues("spring.jpa.open-in-view=true")
                .run(context -> {
                    String value = context.getEnvironment().getProperty("spring.jpa.open-in-view");
                    assertThat(value).isEqualTo("true");
                });
    }

    /**
     * Verifies that an explicit {@code spring.flyway.baseline-on-migrate=false} wins over
     * the framework default.
     */
    @Test
    void flywayBaselineOnMigrate_userOverrideWins_whenExplicitlySet() {
        contextRunner
                .withPropertyValues("spring.flyway.baseline-on-migrate=false")
                .run(context -> {
                    String value = context.getEnvironment().getProperty("spring.flyway.baseline-on-migrate");
                    assertThat(value).isEqualTo("false");
                });
    }
}
