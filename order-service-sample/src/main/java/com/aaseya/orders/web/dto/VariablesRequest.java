package com.aaseya.orders.web.dto;

import java.util.Map;

/**
 * Inbound body carrying a set of process variables, e.g.
 * {@code {"variables": {"approved": true, "reviewer": "sriram"}}}.
 * Used both to complete a user task with parameters and to set variables on a running
 * process instance.
 */
public record VariablesRequest(Map<String, Object> variables) {
}
