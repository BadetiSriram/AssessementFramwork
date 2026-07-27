package com.aaseya.orders.web.dto;

/**
 * Read model for a single process/scope variable. {@code value} is the JSON-encoded value
 * as returned by the Camunda 8.9 Orchestration Cluster API v2 (the "Operate" replacement).
 */
public record VariableView(
        String name,
        String value,
        Long scopeKey) {
}
