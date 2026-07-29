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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Shared by both escalation timers: the CISO review SLA and the 72h regulatory deadline.
 * job.getElementId() is how you tell which one fired. A real one would page whoever is on call;
 * here it logs and writes an audit row so the escalation is visible over the API.
 */
@Component
public class EscalationWorker extends BaseWorker<IncidentJobVars> {

    private static final Logger log = LoggerFactory.getLogger(EscalationWorker.class);

    /** Element id of the escalation task hanging off the CISO review SLA timer. */
    static final String SLA_ESCALATION = "Task_EscalateSla";

    private final IncidentEventService eventService;

    public EscalationWorker(VariableMapper mapper, IdempotencyGuard guard, MeterRegistry meterRegistry,
                            IncidentEventService eventService) {
        super(mapper, guard, meterRegistry);
        this.eventService = eventService;
    }

    @Override
    protected Class<IncidentJobVars> varsType() {
        return IncidentJobVars.class;
    }

    @Override
    protected WorkResult doWork(IncidentJobVars vars, ActivatedJob job) {
        boolean sla = SLA_ESCALATION.equals(job.getElementId());
        String name = sla
                ? "SLA breach escalation (CISO review)"
                : "Regulatory deadline escalation (72h)";
        log.warn("SLA / deadline escalation fired for incident {} at element {}",
                vars.incidentId(), job.getElementId());
        eventService.record(vars.incidentId(), job.getElementId(), name,
                "{\"escalated\":true,\"timer\":\"" + (sla ? "ciso-review-sla" : "regulatory-72h") + "\"}");
        return WorkResult.completed(Map.of("escalated", true));
    }

    @JobWorker(type = "escalate", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
