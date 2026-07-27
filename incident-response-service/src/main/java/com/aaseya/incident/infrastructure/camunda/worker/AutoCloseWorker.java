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
 * Auto-close worker (type {@code auto-close}) for the P4 / false-positive path: the incident is
 * logged and closed without a full response.
 */
@Component
public class AutoCloseWorker extends BaseWorker<IncidentJobVars> {

    private final IncidentService incidentService;

    public AutoCloseWorker(VariableMapper mapper, IdempotencyGuard guard,
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
        incidentService.autoClose(vars.incidentId(), "P4 / false positive — logged and auto-closed");
        return WorkResult.completed();
    }

    @JobWorker(type = "auto-close", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
