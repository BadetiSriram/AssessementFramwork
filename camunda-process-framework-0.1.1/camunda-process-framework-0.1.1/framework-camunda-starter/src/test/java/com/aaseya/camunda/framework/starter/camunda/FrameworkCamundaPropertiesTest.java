package com.aaseya.camunda.framework.starter.camunda;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link FrameworkCamundaProperties} binds correctly in a Spring
 * application context, both with default values and with explicit configuration.
 *
 * <p>Tests use {@link ApplicationContextRunner} for lightweight context construction
 * without starting a full Spring Boot application or Camunda infrastructure.
 */
class FrameworkCamundaPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FrameworkCamundaAutoConfiguration.class);

    /**
     * Verifies that all worker properties resolve to their specified default values
     * when no {@code framework.camunda.*} properties are provided by the application.
     */
    @Test
    void workerDefaults_areAppliedWhenNoPropertiesAreSet() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FrameworkCamundaProperties.class);

            FrameworkCamundaProperties props = context.getBean(FrameworkCamundaProperties.class);
            FrameworkCamundaProperties.Worker worker = props.getWorker();

            assertThat(worker.getMaxJobsActive()).isEqualTo(32);
            assertThat(worker.getPollInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(worker.getRetryBackoff()).isEqualTo(Duration.ofSeconds(5));
            assertThat(worker.getDefaultRetries()).isEqualTo(3);
        });
    }

    /**
     * Verifies that explicit {@code framework.camunda.worker.*} property values override
     * the built-in defaults and are correctly bound to the properties bean.
     */
    @Test
    void workerProperties_bindFromApplicationConfiguration() {
        contextRunner
                .withPropertyValues(
                        "framework.camunda.worker.max-jobs-active=64",
                        "framework.camunda.worker.poll-interval=PT15S"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FrameworkCamundaProperties.class);

                    FrameworkCamundaProperties props = context.getBean(FrameworkCamundaProperties.class);
                    FrameworkCamundaProperties.Worker worker = props.getWorker();

                    assertThat(worker.getMaxJobsActive()).isEqualTo(64);
                    assertThat(worker.getPollInterval()).isEqualTo(Duration.ofSeconds(15));
                    // Values not overridden remain at defaults
                    assertThat(worker.getRetryBackoff()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(worker.getDefaultRetries()).isEqualTo(3);
                });
    }
}
