package com.aaseya.orders.infrastructure.camunda.worker;

import com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard;
import com.aaseya.camunda.framework.core.worker.BaseWorker;
import com.aaseya.camunda.framework.core.worker.VariableMapper;
import com.aaseya.camunda.framework.core.worker.WorkResult;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sample job worker for the {@code TestJobworker} service task in the
 * "Usertask and job worker testing" process (BPMN process id {@code Process_1ef326b}).
 *
 * <p>It requires no input variables and simply writes a few sample output variables back to
 * the process instance, then completes — a minimal template of the framework's
 * {@link BaseWorker} pattern (same shape as {@code ReserveInventoryWorker}, but with no
 * domain dependency).
 *
 * <p>Lives under {@code infrastructure.camunda.worker} because a {@link BaseWorker} subclass
 * imports {@code io.camunda.client} types ({@link ActivatedJob}, {@link JobClient},
 * {@link JobWorker}), which ArchUnit rule #2 only permits there. {@code autoComplete = false}
 * is mandatory — the framework issues the complete/throw-error command itself.
 */
@Component
public class TestJobWorker extends BaseWorker<TestJobVars> {

    public TestJobWorker(VariableMapper mapper,
                         IdempotencyGuard guard,
                         MeterRegistry meterRegistry) {
        super(mapper, guard, meterRegistry);
    }

    @Override
    protected Class<TestJobVars> varsType() {
        return TestJobVars.class;
    }

    @Override
    protected WorkResult doWork(TestJobVars vars, ActivatedJob job) {
        // Sample: emit a few output variables. You can see them afterwards via
        // GET /orchestration/process-instances/{key}/variables (or in Operate).
        return WorkResult.completed(Map.of(
                "testJobWorkerRan", true,
                "testJobWorkerMessage", "Hello from TestJobWorker (service task 'Test job worker')",
                "testJobWorkerNumber", 42,
                "handledProcessInstanceKey", job.getProcessInstanceKey()));
    }

    @JobWorker(type = "TestJobworker", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
