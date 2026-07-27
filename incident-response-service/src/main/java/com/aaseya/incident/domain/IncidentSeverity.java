package com.aaseya.incident.domain;

/**
 * Incident severity, produced by the Incident Classification DMN. Drives downstream SLAs:
 * P1 highest, P4 lowest (P4 = false positive / auto-closed).
 */
public enum IncidentSeverity {
    P1,
    P2,
    P3,
    P4
}
