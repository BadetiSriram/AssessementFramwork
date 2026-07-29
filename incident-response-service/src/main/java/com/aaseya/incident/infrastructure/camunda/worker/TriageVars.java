package com.aaseya.incident.infrastructure.camunda.worker;

/**
 * triageReport is seeded empty at process start and filled in by the AI triage task. Still empty
 * here means that task's error boundary fired and we are on the fallback path.
 */
public record TriageVars(String businessKey, String incidentId, String triageReport) {
}
