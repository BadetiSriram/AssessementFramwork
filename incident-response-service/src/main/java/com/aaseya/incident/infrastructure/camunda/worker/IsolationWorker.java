package com.aaseya.incident.infrastructure.camunda.worker;

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
 * Automated containment.
 *
 * <p>A business error here becomes a BPMN error, which the boundary event on Isolate Systems
 * catches and turns into a task for the incident commander. That is the point: isolation failing
 * is a decision for a human, not something to retry in a loop.
 */
@Component
public class IsolationWorker extends BaseWorker<IsolationVars> {

    public IsolationWorker(VariableMapper mapper, IdempotencyGuard guard, MeterRegistry meterRegistry) {
        super(mapper, guard, meterRegistry);
    }

    @Override
    protected Class<IsolationVars> varsType() {
        return IsolationVars.class;
    }

    @Override
    protected WorkResult doWork(IsolationVars vars, ActivatedJob job) {
        if (vars.forceIsolationFailure()) {
            return WorkResult.businessError("ISOLATION_FAILED",
                    "Automated isolation failed for incident " + vars.incidentId()
                            + ", escalating to the incident commander.");
        }
        // TODO: call the network segmentation / host quarantine APIs
        return WorkResult.completed(Map.of("isolated", true));
    }

    @JobWorker(type = "isolate-systems", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
