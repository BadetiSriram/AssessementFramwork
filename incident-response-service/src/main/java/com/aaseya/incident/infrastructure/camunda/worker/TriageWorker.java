package com.aaseya.incident.infrastructure.camunda.worker;

import com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard;
import com.aaseya.camunda.framework.core.worker.BaseWorker;
import com.aaseya.camunda.framework.core.worker.VariableMapper;
import com.aaseya.camunda.framework.core.worker.WorkResult;
import com.aaseya.incident.application.IncidentEventService;
import com.aaseya.incident.application.IncidentService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Runs straight after the AI triage task, and also on the error path if that task failed.
 *
 * <p>It moves the aggregate to TRIAGED. The triage narrative belongs to the AI task and the DMN
 * input signals are set at process start, so the only other thing to do here is note which of the
 * two incoming paths we arrived on - an empty triageReport means the AI boundary fired.
 */
@Component
public class TriageWorker extends BaseWorker<TriageVars> {

    private final IncidentService incidentService;
    private final IncidentEventService eventService;

    public TriageWorker(VariableMapper mapper, IdempotencyGuard guard,
                        MeterRegistry meterRegistry, IncidentService incidentService,
                        IncidentEventService eventService) {
        super(mapper, guard, meterRegistry);
        this.incidentService = incidentService;
        this.eventService = eventService;
    }

    @Override
    protected Class<TriageVars> varsType() {
        return TriageVars.class;
    }

    @Override
    protected WorkResult doWork(TriageVars vars, ActivatedJob job) {
        incidentService.markTriaged(vars.incidentId());

        boolean aiProducedReport = vars.triageReport() != null && !vars.triageReport().isBlank();
        if (!aiProducedReport) {
            eventService.record(vars.incidentId(), job.getElementId(),
                    "AI threat triage fallback",
                    "{\"reason\":\"AI_STEP_FAILED\",\"fallback\":\"triage-threat worker\"}");
            return WorkResult.completed(Map.of(
                    "triageSource", "FALLBACK",
                    "triageReport", "Triage recorded without AI enrichment (AI step unavailable)."));
        }
        return WorkResult.completed(Map.of("triageSource", "AI"));
    }

    @JobWorker(type = "triage-threat", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
