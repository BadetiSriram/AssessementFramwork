package com.aaseya.incident.web.dto;

import com.aaseya.incident.domain.Incident;

/**
 * Outbound view of an {@link Incident}. A record (not the entity) so it is safe to return from a
 * controller (ArchUnit rule 5).
 */
public record IncidentDto(
        String id,
        String title,
        String source,
        String severity,
        String status) {

    public static IncidentDto from(Incident incident) {
        return new IncidentDto(
                incident.getId().toString(),
                incident.getTitle(),
                incident.getSource(),
                incident.getSeverity() == null ? null : incident.getSeverity().name(),
                incident.status().name());
    }
}
