package com.aaseya.incident.infrastructure.camunda.worker;

/**
 * Input variables for the record-classification worker. {@code severity} is produced by the
 * Incident Classification DMN business rule task that runs immediately before this worker.
 *
 * @param businessKey correlation key (incident id), for idempotency
 * @param incidentId  the incident aggregate id
 * @param severity    DMN output: "P1".."P4"
 */
public record ClassificationVars(String businessKey, String incidentId, String severity) {
}
