package com.aaseya.incident.infrastructure.camunda.worker;

/**
 * Input variables for the automated isolation worker.
 *
 * <p>{@code forceIsolationFailure} is a primitive boolean, so it is effectively optional
 * (absent ⇒ {@code false}) and never trips the framework's required-variable validation. When
 * {@code true}, the worker raises a BPMN business error so the flow escalates to the incident
 * commander (UC4: "failed isolation ... escalates ... rather than silently retrying forever").
 *
 * @param businessKey           correlation key (incident id), for idempotency
 * @param incidentId            the incident aggregate id
 * @param forceIsolationFailure test hook to exercise the escalation path
 */
public record IsolationVars(String businessKey, String incidentId, boolean forceIsolationFailure) {
}
