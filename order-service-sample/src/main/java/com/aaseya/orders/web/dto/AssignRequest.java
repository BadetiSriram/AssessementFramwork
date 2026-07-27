package com.aaseya.orders.web.dto;

/**
 * Inbound body for assigning a user task, e.g. {@code {"assignee": "sriram.badeti"}}.
 */
public record AssignRequest(String assignee) {
}
