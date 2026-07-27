package com.aaseya.camunda.framework.test.process;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link CamundaScenarioTestBase} bootstraps the Spring test context
 * and injects the {@link CamundaClient} and {@link CamundaProcessTestContext} fields
 * correctly.
 *
 * <p>No BPMN file is deployed and no process instance is started — this test exists
 * solely to confirm the harness wires up without errors.</p>
 *
 * <p>The {@code @CamundaSpringProcessTest} annotation is inherited from
 * {@link CamundaScenarioTestBase} and activates the in-memory Camunda engine via
 * the JUnit 5 extension mechanism. {@code @SpringBootTest} here loads the Spring
 * Boot application context so that the beans can be auto-wired into the parent class
 * fields.</p>
 */
@SpringBootTest(classes = CamundaScenarioTestBaseTest.MinimalApp.class)
class CamundaScenarioTestBaseTest extends CamundaScenarioTestBase {

    /**
     * Minimal Spring Boot application class providing just enough context for the
     * Camunda process-test harness to start the in-memory engine and wire the
     * required beans.
     */
    @SpringBootApplication
    static class MinimalApp {
        /* intentionally empty — Spring Boot auto-configuration handles the rest */
    }

    @Test
    void camundaClient_isInjected() {
        assertThat(camundaClient)
                .as("CamundaClient must be auto-wired by the Camunda process test harness")
                .isNotNull();
    }

    @Test
    void testContext_isInjected() {
        assertThat(testContext)
                .as("CamundaProcessTestContext must be auto-wired by the Camunda process test harness")
                .isNotNull();
    }
}
