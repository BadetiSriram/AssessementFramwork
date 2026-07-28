package com.aaseya.incident.web.dto;

import com.aaseya.incident.domain.Incident;

/** Read model for {@link Incident}. Never return the entity itself from a controller. */
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
