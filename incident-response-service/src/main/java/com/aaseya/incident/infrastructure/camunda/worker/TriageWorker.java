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

import java.util.Map;

/**
 * Threat-triage worker (type {@code triage-threat}) — the first automated step.
 *
 * <p><b>Assessment note:</b> UC4 specifies this as an <em>AI connector</em> step (enrichment, IOC
 * lookups, attack-pattern matching). This worker is a runnable placeholder that produces the same
 * shape of structured triage output; replace it with a Camunda AI connector task in the final model.
 *
 * <p>The triage output variables ({@code attackConfirmed}, {@code assetCriticality},
 * {@code dataExposed}) feed the Incident Classification DMN inputs.
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
        // Placeholder triage output feeding the classification DMN.
        return WorkResult.completed(Map.of(
                "triageReport", "Automated triage for incident " + vars.incidentId()
                        + ": enrichment + IOC lookup + attack-pattern match (placeholder).",
                "attackConfirmed", true,
                "assetCriticality", "HIGH",
                "dataExposed", true));
    }

    @JobWorker(type = "triage-threat", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
