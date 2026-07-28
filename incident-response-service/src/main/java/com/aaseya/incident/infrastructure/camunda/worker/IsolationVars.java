package com.aaseya.incident.infrastructure.camunda.worker;

/**
 * forceIsolationFailure is deliberately a primitive: that makes it optional as far as the
 * framework's required-variable check is concerned, so we don't have to set it on every path.
 */
public record IsolationVars(String businessKey, String incidentId, boolean forceIsolationFailure) {
}
