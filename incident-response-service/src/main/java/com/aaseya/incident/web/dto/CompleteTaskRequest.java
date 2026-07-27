package com.aaseya.incident.web.dto;

import java.util.Map;

/**
 * Inbound body to complete a user task from the API, e.g.
 * {@code {"completedBy":"soc.analyst","variables":{"containmentVerified":true}}}.
 * The variables are submitted to Camunda (as if from Tasklist) and recorded as the task outcome.
 */
public record CompleteTaskRequest(String completedBy, Map<String, Object> variables) {
}
