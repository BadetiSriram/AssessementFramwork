package com.aaseya.orders.web.dto;

/**
 * Read model for a user (human) task, as returned by the Camunda 8.9 Orchestration Cluster
 * API v2 user-task search (the "Tasklist" replacement).
 */
public record UserTaskView(
        Long userTaskKey,
        String name,
        String elementId,
        String assignee,
        String state) {
}
