package com.aaseya.incident.infrastructure.camunda.worker;

import com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard;
import com.aaseya.camunda.framework.core.worker.BaseWorker;
import com.aaseya.camunda.framework.core.worker.VariableMapper;
import com.aaseya.camunda.framework.core.worker.WorkResult;
import com.aaseya.incident.application.IncidentService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Threat-triage worker (type {@code triage-threat}) — the first automated step.
 *
 * <p><b>Role:</b> the AI threat-triage is done by an <em>AI connector</em> task
 * ({@code Task_TriageAI}) that writes {@code triageReport}. This worker runs right after it (on the
 * happy path, and as the fallback if the AI step fails) to update the domain — so it deliberately
 * does NOT write {@code triageReport} (that belongs to the AI step).
 *
 * <p>The structured signals the classification / regulatory DMNs consume
 * ({@code attackConfirmed}, {@code assetCriticality}, {@code dataExposed}, {@code recordCount}) are
 * supplied at process start from the request (see {@code IncidentService.raiseIncident}, with P1
 * defaults), so this worker no longer hardcodes them — the DMN outcome is now request-driven.
 */
@Component
public class TriageWorker extends BaseWorker<IncidentJobVars> {

    private final IncidentService incidentService;

    public TriageWorker(VariableMapper mapper, IdempotencyGuard guard,
                        MeterRegistry meterRegistry, IncidentService incidentService) {
        super(mapper, guard, meterRegistry);
        this.incidentService = incidentService;
    }

    @Override
    protected Class<IncidentJobVars> varsType() {
        return IncidentJobVars.class;
    }

    @Override
    protected WorkResult doWork(IncidentJobVars vars, ActivatedJob job) {
        incidentService.markTriaged(vars.incidentId());
        // The DMN input signals (attackConfirmed / assetCriticality / dataExposed / recordCount) are
        // set at process start from the request, so this worker only advances the domain state.
        return WorkResult.completed();
    }

    @JobWorker(type = "triage-threat", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
