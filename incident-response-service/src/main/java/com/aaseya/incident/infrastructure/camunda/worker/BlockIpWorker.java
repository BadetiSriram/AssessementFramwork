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

/** Ad-hoc action. The commander can fire this more than once as new IOCs turn up. */
@Component
public class BlockIpWorker extends BaseWorker<IncidentJobVars> {

    public BlockIpWorker(VariableMapper mapper, IdempotencyGuard guard, MeterRegistry meterRegistry) {
        super(mapper, guard, meterRegistry);
    }

    @Override
    protected Class<IncidentJobVars> varsType() {
        return IncidentJobVars.class;
    }

    @Override
    protected WorkResult doWork(IncidentJobVars vars, ActivatedJob job) {
        return WorkResult.completed(Map.of("ipBlocked", true));
    }

    @JobWorker(type = "block-ip", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
