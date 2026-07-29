package com.aaseya.incident.infrastructure.camunda.worker;

/**
 * severity ("P1".."P4") comes from the classification DMN task just before this worker.
 * slaOverride is the demo hook: blank means "derive the SLA from the severity".
 */
public record ClassificationVars(String businessKey, String incidentId, String severity,
                                 String slaOverride) {
}
