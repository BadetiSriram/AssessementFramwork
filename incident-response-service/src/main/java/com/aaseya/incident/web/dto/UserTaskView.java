package com.aaseya.incident.web.dto;

/**
 * View of a Camunda user task for an incident (from the 8.9 Orchestration Cluster API v2).
 */
public record UserTaskView(
        long userTaskKey,
        String elementId,
        String name,
        String assignee,
        String state) {
}
