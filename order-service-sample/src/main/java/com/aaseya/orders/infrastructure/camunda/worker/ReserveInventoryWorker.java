package com.aaseya.orders.infrastructure.camunda.worker;

import com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard;
import com.aaseya.camunda.framework.core.worker.BaseWorker;
import com.aaseya.camunda.framework.core.worker.VariableMapper;
import com.aaseya.camunda.framework.core.worker.WorkResult;
import com.aaseya.orders.application.OrderService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Job worker for the {@code reserve-inventory} service task in the order-fulfillment
 * process.
 *
 * <p><b>Why this class lives under {@code infrastructure.camunda}:</b> a
 * {@link BaseWorker} subclass necessarily imports {@code io.camunda.client} types
 * ({@link ActivatedJob}, {@link JobClient}, {@link JobWorker}). ArchUnit rule 2
 * (ONLY_INFRASTRUCTURE_CAMUNDA_MAY_IMPORT_CAMUNDA_CLIENT) permits that import <em>only</em>
 * here. Business logic itself is delegated to {@link OrderService} — the worker is a thin
 * inbound adapter and holds no rules of its own.
 *
 * <p>The framework's {@code BaseWorker.execute(...)} owns variable binding, MDC, the
 * idempotency short-circuit, error classification, and metrics. This subclass only supplies
 * {@link #varsType()} and {@link #doWork(ReserveInventoryVars, ActivatedJob)}, and delegates
 * the annotated method to {@code execute}. {@code autoComplete = false} is mandatory — the
 * framework issues the complete / throw-error command itself.
 */
@Component
public class ReserveInventoryWorker extends BaseWorker<ReserveInventoryVars> {

    private final OrderService orderService;

    public ReserveInventoryWorker(VariableMapper mapper,
                                  IdempotencyGuard guard,
                                  MeterRegistry meterRegistry,
                                  OrderService orderService) {
        super(mapper, guard, meterRegistry);
        this.orderService = orderService;
    }

    @Override
    protected Class<ReserveInventoryVars> varsType() {
        return ReserveInventoryVars.class;
    }

    @Override
    protected WorkResult doWork(ReserveInventoryVars vars, ActivatedJob job) {
        boolean reserved = orderService.reserveInventory(vars.orderId());
        if (!reserved) {
            // Domain failure → BPMN error boundary event, not a technical retry.
            return WorkResult.businessError("ORDER_NOT_FOUND",
                    "No order with id " + vars.orderId());
        }
        return WorkResult.completed(Map.of("reserved", true));
    }

    /**
     * Camunda entry point. Delegates to the framework skeleton, which runs the fixed
     * variable-bind → idempotency → validate → doWork → dispatch → metrics pipeline.
     *
     * @param client Camunda job client
     * @param job    the activated job
     */
    @JobWorker(type = "reserve-inventory", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        execute(client, job);
    }
}
