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
 * Records the DMN classification result on the incident aggregate (type
 * {@code record-classification}). Runs immediately after the Incident Classification DMN business
 * rule task, which sets the {@code severity} process variable ("P1".."P4").
 */
@Component
public class RecordClassificationWorker extends BaseWorker<ClassificationVars> {

    private final IncidentService incidentService;

    public RecordClassificationWorker(VariableMapper mapper, IdempotencyGuard guard,
                                      MeterRegistry meterRegistry, IncidentService incidentService) {
        super(mapper, guard, meterRegistry);
        this.incidentService = incidentService;
    }

    @Override
    protected Class<ClassificationVars> varsType() {
        return ClassificationVars.class;
    }

    @Override
    protected WorkResult doWork(ClassificationVars vars, ActivatedJob job) {
        incidentService.recordClassification(vars.incidentId(), vars.severity());
        return WorkResult.completed();
    }

    @JobWorker(type = "record-classification", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
