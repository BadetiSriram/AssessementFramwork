package com.aaseya.incident.domain;

/**
 * Lifecycle states for an {@link Incident}. Legal transitions are enforced by
 * {@link Incident#allowedTransitions(IncidentStatus)}.
 *
 * <pre>
 *   RAISED ─▶ TRIAGED ─▶ CLASSIFIED ─▶ RECOVERING ─▶ CLOSED
 *     │          │            │
 *     └──────────┴────────────┴─▶ AUTO_CLOSED   (P4 / false positive, or early close)
 * </pre>
 */
public enum IncidentStatus {
    RAISED,
    TRIAGED,
    CLASSIFIED,
    RECOVERING,
    CLOSED,
    AUTO_CLOSED
}
