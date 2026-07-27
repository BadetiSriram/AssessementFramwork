package com.aaseya.orders.infrastructure.camunda.worker;

/**
 * Typed input variables for the {@code reserve-inventory} job.
 *
 * <p>The framework binds the process variables to this record. Every non-{@code null}
 * record component is treated as required (the framework's VariableMapper throws if a
 * required variable is missing). The {@code businessKey} component is what
 * {@code BaseWorker} reads to drive idempotency — replays of the same
 * {@code (businessKey, elementId)} complete silently.
 *
 * @param businessKey correlation key (the order id), also used for idempotency
 * @param orderId     the order aggregate id to reserve
 */
public record ReserveInventoryVars(String businessKey, String orderId) {
}
