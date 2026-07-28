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

/** Ad-hoc action: kill the compromised credentials. */
@Component
public class RevokeCredentialsWorker extends BaseWorker<IncidentJobVars> {

    public RevokeCredentialsWorker(VariableMapper mapper, IdempotencyGuard guard, MeterRegistry meterRegistry) {
        super(mapper, guard, meterRegistry);
    }

    @Override
    protected Class<IncidentJobVars> varsType() {
        return IncidentJobVars.class;
    }

    @Override
    protected WorkResult doWork(IncidentJobVars vars, ActivatedJob job) {
        return WorkResult.completed(Map.of("credentialsRevoked", true));
    }

    @JobWorker(type = "revoke-credentials", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
