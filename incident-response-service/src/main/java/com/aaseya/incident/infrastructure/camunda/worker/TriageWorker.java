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
 * Runs straight after the AI triage task, and also on the error path if that task failed.
 *
 * <p>It only moves the aggregate to TRIAGED. The triage narrative belongs to the AI task and the
 * DMN input signals are set at process start, so there is nothing else to write here.
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
        return WorkResult.completed();
    }

    @JobWorker(type = "triage-threat", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
