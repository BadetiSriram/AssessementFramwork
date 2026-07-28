package com.aaseya.incident.domain;

/**
 * Incident lifecycle. See {@link Incident#allowedTransitions} for what is legal.
 *
 * <pre>
 *   RAISED -> TRIAGED -> CLASSIFIED -> RECOVERING -> CLOSED
 * </pre>
 *
 * AUTO_CLOSED is reachable from the first three states (P4 / false positive).
 */
public enum IncidentStatus {
    RAISED,
    TRIAGED,
    CLASSIFIED,
    RECOVERING,
    CLOSED,
    AUTO_CLOSED
}
