package com.aaseya.incident.web.dto;

import java.util.Map;

/**
 * e.g. {@code {"completedBy":"soc.analyst","variables":{"containmentVerified":true}}}
 *
 * <p>The variables go to Camunda as the task's output and are stored as the outcome. Sending
 * extra ones is harmless, which is why the Postman collection gets away with one shared body.
 */
public record CompleteTaskRequest(String completedBy, Map<String, Object> variables) {
}
