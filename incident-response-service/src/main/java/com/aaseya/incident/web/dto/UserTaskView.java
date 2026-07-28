package com.aaseya.incident.web.dto;

/** A user task as returned by the Orchestration Cluster API. */
public record UserTaskView(
        long userTaskKey,
        String elementId,
        String name,
        String assignee,
        String state) {
}
