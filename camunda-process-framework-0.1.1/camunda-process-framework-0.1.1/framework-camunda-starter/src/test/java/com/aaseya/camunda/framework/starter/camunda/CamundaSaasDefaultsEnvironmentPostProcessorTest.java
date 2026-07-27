package com.aaseya.camunda.framework.starter.camunda;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the behaviour of {@link CamundaSaasDefaultsEnvironmentPostProcessor}.
 *
 * <p>The post-processor inserts {@code camunda.client.mode=saas} at the lowest
 * precedence in the environment. These tests confirm:
 * <ol>
 *   <li>The default is present when the property is not set by the application.</li>
 *   <li>An explicit application-level value takes precedence over the injected default.</li>
 * </ol>
 *
 * <p>Tests use {@link ApplicationContextRunner} for lightweight context construction
 * without starting a full Spring Boot application.
 */
class CamundaSaasDefaultsEnvironmentPostProcessorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FrameworkCamundaAutoConfiguration.class)
            .withInitializer(applicationContext -> {
                CamundaSaasDefaultsEnvironmentPostProcessor postProcessor =
                        new CamundaSaasDefaultsEnvironmentPostProcessor();
                postProcessor.postProcessEnvironment(
                        applicationContext.getEnvironment(),
                        null
                );
            });

    /**
     * Verifies that {@code camunda.client.mode} resolves to {@code saas} when
     * no explicit value is provided by the application.
     */
    @Test
    void camundaClientMode_defaultsToSaas_whenNotExplicitlySet() {
        contextRunner.run(context -> {
            String mode = context.getEnvironment().getProperty("camunda.client.mode");
            assertThat(mode).isEqualTo("saas");
        });
    }

    /**
     * Verifies that an explicit application-level {@code camunda.client.mode} property
     * wins over the {@code saas} default injected by the post-processor.
     */
    @Test
    void camundaClientMode_userOverrideWins_whenExplicitlySet() {
        contextRunner
                .withPropertyValues("camunda.client.mode=self-managed")
                .run(context -> {
                    String mode = context.getEnvironment().getProperty("camunda.client.mode");
                    assertThat(mode).isEqualTo("self-managed");
                });
    }
}
