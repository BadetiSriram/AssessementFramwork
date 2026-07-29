package com.aaseya.incident.infrastructure.camunda.worker;

import com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard;
import com.aaseya.camunda.framework.core.worker.BaseWorker;
import com.aaseya.camunda.framework.core.worker.VariableMapper;
import com.aaseya.camunda.framework.core.worker.WorkResult;
import com.aaseya.incident.application.IncidentEventService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fallback for the AI post-incident report. Only reached when the AI task's boundary error
 * fires, so the flow can still reach closure without an LLM. Writes the same variable.
 */
@Component
public class PostIncidentReportWorker extends BaseWorker<IncidentJobVars> {

    private final IncidentEventService eventService;

    public PostIncidentReportWorker(VariableMapper mapper, IdempotencyGuard guard,
                                    MeterRegistry meterRegistry, IncidentEventService eventService) {
        super(mapper, guard, meterRegistry);
        this.eventService = eventService;
    }

    @Override
    protected Class<IncidentJobVars> varsType() {
        return IncidentJobVars.class;
    }

    @Override
    protected WorkResult doWork(IncidentJobVars vars, ActivatedJob job) {
        // Reaching this worker at all means the AI boundary fired, so it is worth recording.
        eventService.record(vars.incidentId(), job.getElementId(),
                "AI post-incident report fallback",
                "{\"reason\":\"AI_STEP_FAILED\",\"fallback\":\"generate-report worker\"}");
        return WorkResult.completed(Map.of(
                "postIncidentReport",
                "Post-incident report for " + vars.incidentId()
                        + ": timeline, root cause, impact, lessons learned. (AI step unavailable.)"));
    }

    @JobWorker(type = "generate-report", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
