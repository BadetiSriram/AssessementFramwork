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
 * Post-incident report worker (type {@code generate-report}).
 *
 * <p><b>Assessment note:</b> UC4 specifies this as an <em>AI connector</em> step (post-incident
 * report + lessons learned). This worker is a runnable placeholder producing the same output
 * variable; replace it with a Camunda AI connector task in the final model.
 */
@Component
public class PostIncidentReportWorker extends BaseWorker<IncidentJobVars> {

    public PostIncidentReportWorker(VariableMapper mapper, IdempotencyGuard guard, MeterRegistry meterRegistry) {
        super(mapper, guard, meterRegistry);
    }

    @Override
    protected Class<IncidentJobVars> varsType() {
        return IncidentJobVars.class;
    }

    @Override
    protected WorkResult doWork(IncidentJobVars vars, ActivatedJob job) {
        return WorkResult.completed(Map.of(
                "postIncidentReport",
                "Post-incident report for " + vars.incidentId()
                        + ": timeline, root cause, impact, and lessons learned (placeholder)."));
    }

    @JobWorker(type = "generate-report", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
