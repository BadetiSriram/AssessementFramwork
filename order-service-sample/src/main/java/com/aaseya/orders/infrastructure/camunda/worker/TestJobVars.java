package com.aaseya.orders.infrastructure.camunda.worker;

/**
 * Input variables for the {@code TestJobworker} sample job — intentionally EMPTY.
 *
 * <p>The framework's {@code VariableMapper} treats every record component as required, so an
 * empty record means the worker requires <b>no</b> input variables and the process can start
 * with or without any. (To make a specific field optional instead, annotate it with any
 * {@code @Nullable} annotation.)
 */
public record TestJobVars() {
}
