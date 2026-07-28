package com.aaseya.incident.infrastructure.camunda.worker;

import com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard;
import com.aaseya.camunda.framework.core.worker.BaseWorker;
import com.aaseya.camunda.framework.core.worker.VariableMapper;
import com.aaseya.camunda.framework.core.worker.WorkResult;
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
 * job.getElementId() is how you tell which one fired. For now it just logs; a real one would
 * page whoever is on call.
 */
@Component
public class EscalationWorker extends BaseWorker<IncidentJobVars> {

    private static final Logger log = LoggerFactory.getLogger(EscalationWorker.class);

    public EscalationWorker(VariableMapper mapper, IdempotencyGuard guard, MeterRegistry meterRegistry) {
        super(mapper, guard, meterRegistry);
    }

    @Override
    protected Class<IncidentJobVars> varsType() {
        return IncidentJobVars.class;
    }

    @Override
    protected WorkResult doWork(IncidentJobVars vars, ActivatedJob job) {
        log.warn("SLA / deadline escalation fired for incident {} at element {}",
                vars.incidentId(), job.getElementId());
        return WorkResult.completed(Map.of("escalated", true));
    }

    @JobWorker(type = "escalate", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
