package com.aaseya.incident.infrastructure.camunda.worker;

/** severity ("P1".."P4") comes from the classification DMN task just before this worker. */
public record ClassificationVars(String businessKey, String incidentId, String severity) {
}
