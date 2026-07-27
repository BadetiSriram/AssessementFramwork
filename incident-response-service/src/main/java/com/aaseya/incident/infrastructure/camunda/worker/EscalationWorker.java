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
 * Escalation worker (type {@code escalate}) reached from the SLA timers and the 72-hour
 * regulatory-deadline timer. Notifies the escalation target (CISO / manager). A placeholder that
 * logs and records the escalation; a real implementation would page/notify the on-call.
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
