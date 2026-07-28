package com.aaseya.incident.infrastructure.camunda.worker;

/**
 * What most of the workers need. BaseWorker keys the idempotency guard off businessKey, so a
 * redelivered alert or a retried job won't apply the same side effect twice.
 */
public record IncidentJobVars(String businessKey, String incidentId) {
}
