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
 * Recovery worker (type {@code restore-services}) — restores services from a clean state; the
 * process then routes to the integrity-verification human task. Marks the incident RECOVERING.
 */
@Component
public class RestoreServicesWorker extends BaseWorker<IncidentJobVars> {

    private final IncidentService incidentService;

    public RestoreServicesWorker(VariableMapper mapper, IdempotencyGuard guard,
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
        incidentService.markRecovering(vars.incidentId());
        return WorkResult.completed(Map.of("servicesRestored", true));
    }

    @JobWorker(type = "restore-services", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
