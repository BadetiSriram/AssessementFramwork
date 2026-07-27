package com.aaseya.incident.infrastructure.camunda.worker;

/**
 * Shared input variables for most incident-response job workers.
 *
 * <p>{@code businessKey} (= the incident id) is what {@code BaseWorker} reads to drive the
 * {@code IdempotencyGuard}, so SIEM re-delivery / job retries are safe (UC4 requirement).
 *
 * @param businessKey correlation key (the incident id), also used for idempotency
 * @param incidentId  the incident aggregate id
 */
public record IncidentJobVars(String businessKey, String incidentId) {
}
