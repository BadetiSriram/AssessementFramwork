package com.aaseya.incident.domain;

import com.aaseya.camunda.framework.core.audit.AuditableEntity;
import com.aaseya.camunda.framework.starter.data.AuditColumnListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Incident aggregate for Use Case 4 (Cyber Security Incident Response).
 *
 * <p>Extends {@link AuditableEntity}&lt;{@link IncidentStatus}&gt; so status changes go through a
 * validated state machine ({@link #transition} throws {@code IllegalStateTransitionException} on an
 * illegal move). {@code @EntityListeners(AuditColumnListener.class)} stamps the four audit columns.
 *
 * <p>Framework-pure per ArchUnit rule 3 — no Spring Web / servlet / {@code io.camunda} imports.
 * Table {@code incidents} is created by {@code V2__incident_tables.sql}; {@code ddl-auto=validate}.
 */
@Entity
@Table(name = "incidents")
@EntityListeners(AuditColumnListener.class)
public class Incident extends AuditableEntity<IncidentStatus> {

    private static final Logger log = LoggerFactory.getLogger(Incident.class);

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Correlation key carried through the BPMN process; drives the idempotency guard. */
    @Column(name = "business_key", nullable = false, updatable = false)
    private String businessKey;

    @Column(name = "title", nullable = false)
    private String title;

    /** Origin of the alert, e.g. "SIEM". */
    @Column(name = "source", nullable = false)
    private String source;

    /** Null until the classification DMN runs. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status = IncidentStatus.RAISED;

    /** Camunda process instance key, set once the incident-response process is started. */
    @Column(name = "process_instance_key")
    private Long processInstanceKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    @Column(name = "updated_by")
    private String updatedBy;

    protected Incident() {
    }

    /** Factory for a newly raised incident. The id doubles as the business key. */
    public static Incident raise(String title, String source) {
        Incident incident = new Incident();
        incident.id = UUID.randomUUID();
        incident.businessKey = incident.id.toString();
        incident.title = title;
        incident.source = source;
        incident.status = IncidentStatus.RAISED;
        return incident;
    }

    // ---- Domain operations (each guarded by the state machine) ----

    public void markTriaged() {
        transition(IncidentStatus.TRIAGED, "Threat triaged");
    }

    /** Record the DMN classification result and move to CLASSIFIED. */
    public void classify(IncidentSeverity severity) {
        this.severity = severity;
        transition(IncidentStatus.CLASSIFIED, "Classified as " + severity);
    }

    public void markRecovering() {
        transition(IncidentStatus.RECOVERING, "Recovery in progress");
    }

    public void close() {
        transition(IncidentStatus.CLOSED, "Incident closed");
    }

    public void autoClose(String reason) {
        transition(IncidentStatus.AUTO_CLOSED, reason);
    }

    // ---- AuditableEntity contract ----

    @Override
    protected Set<IncidentStatus> allowedTransitions(IncidentStatus from) {
        return switch (from) {
            case RAISED      -> EnumSet.of(IncidentStatus.TRIAGED, IncidentStatus.AUTO_CLOSED);
            case TRIAGED     -> EnumSet.of(IncidentStatus.CLASSIFIED, IncidentStatus.AUTO_CLOSED);
            case CLASSIFIED  -> EnumSet.of(IncidentStatus.RECOVERING, IncidentStatus.AUTO_CLOSED);
            case RECOVERING  -> EnumSet.of(IncidentStatus.CLOSED);
            case CLOSED, AUTO_CLOSED -> EnumSet.noneOf(IncidentStatus.class);
        };
    }

    @Override
    protected IncidentStatus getStatus() {
        return status;
    }

    @Override
    protected void setStatus(IncidentStatus status) {
        this.status = status;
    }

    @Override
    protected void appendAuditNote(String note, IncidentStatus from, IncidentStatus to) {
        log.info("Incident {} transition {} -> {}: {}", id, from, to, note);
    }

    // ---- Accessors ----

    public UUID getId() {
        return id;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getTitle() {
        return title;
    }

    public String getSource() {
        return source;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public IncidentStatus status() {
        return status;
    }

    public Long getProcessInstanceKey() {
        return processInstanceKey;
    }

    public void setProcessInstanceKey(Long processInstanceKey) {
        this.processInstanceKey = processInstanceKey;
    }
}
