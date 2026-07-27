package com.aaseya.camunda.framework.starter.data;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link FrameworkDataProperties} binds correctly in a Spring application
 * context, both with default values and with explicit configuration overrides.
 *
 * <p>Tests use {@link ApplicationContextRunner} for lightweight context construction
 * without starting a full Spring Boot application or requiring a DataSource.
 */
class FrameworkDataPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FrameworkDataAutoConfiguration.class);

    /**
     * Verifies that the {@code audit} sub-section resolves to its default values when no
     * {@code framework.data.audit.*} properties are provided.
     */
    @Test
    void auditDefaults_areAppliedWhenNoPropertiesAreSet() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FrameworkDataProperties.class);

            FrameworkDataProperties props = context.getBean(FrameworkDataProperties.class);
            FrameworkDataProperties.Audit audit = props.getAudit();

            assertThat(audit.isEnabled()).isTrue();
            assertThat(audit.getCreatedByHeader()).isEqualTo("X-User-Id");
        });
    }

    /**
     * Verifies that the {@code flyway} sub-section resolves to its default values when no
     * {@code framework.data.flyway.*} properties are provided.
     */
    @Test
    void flywayDefaults_areAppliedWhenNoPropertiesAreSet() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FrameworkDataProperties.class);

            FrameworkDataProperties props = context.getBean(FrameworkDataProperties.class);
            FrameworkDataProperties.Flyway flyway = props.getFlyway();

            assertThat(flyway.isEnforceNamingConvention()).isTrue();
            assertThat(flyway.getExpectedLocations()).containsExactly("classpath:db/migration");
        });
    }

    /**
     * Verifies that explicit {@code framework.data.audit.*} values override the defaults
     * and are correctly bound.
     */
    @Test
    void auditProperties_bindFromApplicationConfiguration() {
        contextRunner
                .withPropertyValues(
                        "framework.data.audit.enabled=false",
                        "framework.data.audit.created-by-header=X-Custom-User"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FrameworkDataProperties.class);

                    FrameworkDataProperties props = context.getBean(FrameworkDataProperties.class);
                    FrameworkDataProperties.Audit audit = props.getAudit();

                    assertThat(audit.isEnabled()).isFalse();
                    assertThat(audit.getCreatedByHeader()).isEqualTo("X-Custom-User");
                });
    }

    /**
     * Verifies that explicit {@code framework.data.flyway.*} values override the defaults
     * and are correctly bound.
     */
    @Test
    void flywayProperties_bindFromApplicationConfiguration() {
        contextRunner
                .withPropertyValues(
                        "framework.data.flyway.enforce-naming-convention=false",
                        "framework.data.flyway.expected-locations[0]=classpath:db/custom"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FrameworkDataProperties.class);

                    FrameworkDataProperties props = context.getBean(FrameworkDataProperties.class);
                    FrameworkDataProperties.Flyway flyway = props.getFlyway();

                    assertThat(flyway.isEnforceNamingConvention()).isFalse();
                    assertThat(flyway.getExpectedLocations()).containsExactly("classpath:db/custom");
                });
    }

    /**
     * Verifies that {@link FrameworkDataProperties.Audit} and
     * {@link FrameworkDataProperties.Flyway} instances are non-null even when no
     * properties are configured.
     */
    @Test
    void nestedObjects_areNeverNull() {
        contextRunner.run(context -> {
            FrameworkDataProperties props = context.getBean(FrameworkDataProperties.class);
            assertThat(props.getAudit()).isNotNull();
            assertThat(props.getFlyway()).isNotNull();
            assertThat(props.getFlyway().getExpectedLocations()).isNotNull();
        });
    }
}
