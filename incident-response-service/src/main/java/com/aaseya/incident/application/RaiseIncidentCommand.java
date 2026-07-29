package com.aaseya.incident.application;

import java.util.List;

/**
 * What the caller asked for when raising an incident, with the web DTO left at the boundary.
 * Every field except title and source is optional; {@link IncidentService} applies the defaults.
 */
public record RaiseIncidentCommand(
        String title,
        String source,
        Boolean forceIsolationFailure,
        Boolean attackConfirmed,
        String assetCriticality,
        Boolean dataExposed,
        Integer recordCount,
        Boolean forceAiFailure,
        String slaDuration,
        String regulatoryDeadline,
        List<String> responseActions) {
}
