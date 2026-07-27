package com.aaseya.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Record of a completed human (Tasklist) task for an incident — captures the form data submitted
 * by the persona (e.g. containment verification, forensic analysis, CISO review). Persisted to
 * {@code incident_task_outcomes} whenever a user task is completed via the API.
 *
 * <p>Framework-pure (ArchUnit rule 3): no Spring Web / servlet / io.camunda imports.
 */
@Entity
@Table(name = "incident_task_outcomes")
public class IncidentTaskOutcome {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "user_task_key", nullable = false)
    private long userTaskKey;

    @Column(name = "element_id")
    private String elementId;

    @Column(name = "task_name")
    private String taskName;

    @Column(name = "completed_by")
    private String completedBy;

    /** JSON of the submitted form variables. */
    @Column(name = "outcome", columnDefinition = "text")
    private String outcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IncidentTaskOutcome() {
    }

    public static IncidentTaskOutcome of(UUID incidentId, long userTaskKey, String elementId,
                                         String taskName, String completedBy, String outcomeJson) {
        IncidentTaskOutcome o = new IncidentTaskOutcome();
        o.id = UUID.randomUUID();
        o.incidentId = incidentId;
        o.userTaskKey = userTaskKey;
        o.elementId = elementId;
        o.taskName = taskName;
        o.completedBy = completedBy;
        o.outcome = outcomeJson;
        o.createdAt = Instant.now();
        return o;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public long getUserTaskKey() {
        return userTaskKey;
    }

    public String getElementId() {
        return elementId;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
