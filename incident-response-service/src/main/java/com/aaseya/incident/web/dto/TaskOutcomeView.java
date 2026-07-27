package com.aaseya.incident.web.dto;

import com.aaseya.incident.domain.IncidentTaskOutcome;

/**
 * Outbound view of a recorded human-task outcome (safe to return from a controller — not an entity).
 */
public record TaskOutcomeView(
        long userTaskKey,
        String elementId,
        String taskName,
        String completedBy,
        String outcome,
        String completedAt) {

    public static TaskOutcomeView from(IncidentTaskOutcome o) {
        return new TaskOutcomeView(
                o.getUserTaskKey(),
                o.getElementId(),
                o.getTaskName(),
                o.getCompletedBy(),
                o.getOutcome(),
                o.getCreatedAt() == null ? null : o.getCreatedAt().toString());
    }
}
