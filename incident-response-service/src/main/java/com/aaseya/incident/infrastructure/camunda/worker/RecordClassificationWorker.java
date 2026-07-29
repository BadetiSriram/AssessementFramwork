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

/** Stores the DMN severity on the aggregate and derives the SLA the timers run on. */
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
        // the CISO review boundary timer reads slaDuration, so it has to be an ISO-8601 duration
        String slaDuration = vars.slaOverride() != null && !vars.slaOverride().isBlank()
                ? vars.slaOverride()
                : switch (vars.severity()) {
                    case "P1" -> "PT4H";
                    case "P2" -> "PT8H";
                    case "P3" -> "PT24H";
                    default   -> "PT72H";
                };
        return WorkResult.completed(Map.of("slaDuration", slaDuration));
    }

    @JobWorker(type = "record-classification", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
