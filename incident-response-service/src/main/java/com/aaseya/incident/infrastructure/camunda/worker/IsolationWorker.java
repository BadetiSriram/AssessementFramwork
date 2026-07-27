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
 * Automated containment/isolation worker (type {@code isolate-systems}).
 *
 * <p>On failure it returns {@code WorkResult.businessError("ISOLATION_FAILED", ...)}, which the
 * framework turns into a BPMN error (throw-error command). The BPMN must catch it with an error
 * boundary event that escalates to the incident commander — UC4: "failed isolation raises a BPMN
 * error that escalates to the incident commander rather than silently retrying forever."
 *
 * <p>Idempotent: keyed on {@code businessKey} (incident id); real isolation actions must also be
 * naturally idempotent (re-applying the same isolation is a no-op).
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
                            + " — escalating to the incident commander.");
        }
        // Placeholder for real isolation (network segmentation, host quarantine, ...).
        return WorkResult.completed(Map.of("isolated", true));
    }

    @JobWorker(type = "isolate-systems", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
