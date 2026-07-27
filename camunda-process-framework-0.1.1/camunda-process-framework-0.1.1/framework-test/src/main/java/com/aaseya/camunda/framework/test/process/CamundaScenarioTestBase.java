package com.aaseya.camunda.framework.test.process;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * Abstract base class for process-scenario tests that use the Camunda in-memory
 * process runtime provided by {@code camunda-process-test-spring}.
 *
 * <h2>Purpose</h2>
 * <p>This class bootstraps the {@link CamundaSpringProcessTest} Spring extension and
 * pre-wires the two collaborators that scenario tests need most:
 * <ul>
 *   <li>{@link #camundaClient} — to start process instances, complete user tasks, and
 *       send messages.</li>
 *   <li>{@link #testContext} — to access the in-memory engine, deploy resources, and
 *       control the clock in time-based tests.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>Extend this class in a consuming service's test module and add scenario methods:</p>
 * <pre>{@code
 * @SpringBootTest
 * class MyProcessScenarioTest extends CamundaScenarioTestBase {
 *
 *     @Test
 *     void happyPath_processCompletes() throws Exception {
 *         testContext.deployProcess("my-process.bpmn");
 *         long instanceKey = startProcess("my-process", Map.of("input", "value"));
 *         // Write assertions directly using the Camunda 8.9 selector API, e.g.:
 *         // CamundaAssert.assertThat(ProcessInstanceSelectors.byKey(instanceKey)).isCompleted();
 *     }
 * }
 * }</pre>
 *
 * <h2>Scope contract</h2>
 * <p>This class provides <em>only</em> the harness infrastructure. All scenario-specific
 * BPMN deployments, variable maps, and assertions belong in the consuming service's own
 * test classes. Consumers write their own assertion calls using
 * {@code CamundaAssert.assertThat(ProcessInstanceSelectors.byKey(...))} (or the
 * appropriate 8.9 selector API).</p>
 */
@CamundaSpringProcessTest
public abstract class CamundaScenarioTestBase {

    /**
     * The Camunda REST client, auto-wired by the Spring test context.
     *
     * <p>Use this to start process instances, complete user tasks, publish messages,
     * or make any other API calls to the in-memory Camunda engine during tests.</p>
     */
    @Autowired
    protected CamundaClient camundaClient;

    /**
     * The Camunda process test context, auto-wired by the Spring test context.
     *
     * <p>Use this to deploy BPMN/DMN resources before a test, access engine state,
     * or control the virtual clock for timer-based scenarios.</p>
     */
    @Autowired
    protected CamundaProcessTestContext testContext;

    /**
     * Starts a process instance by BPMN process ID and waits synchronously for the
     * command to be accepted by the in-memory engine.
     *
     * @param bpmnProcessId the BPMN process ID as declared in the {@code <process id="...">}
     *                      attribute of the BPMN file
     * @param variables     initial process variables; pass {@code Map.of()} for none
     * @return the process instance key assigned by the engine
     */
    protected long startProcess(String bpmnProcessId, Map<String, Object> variables) {
        return camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId(bpmnProcessId)
                .latestVersion()
                .variables(variables)
                .send()
                .join()
                .getProcessInstanceKey();
    }
}
