package com.aaseya.incident.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound body for {@code POST /incidents} (simulates a SIEM alert).
 *
 * @param title                 short incident title (required)
 * @param source                alert source, e.g. "SIEM" (required)
 * @param forceIsolationFailure test hook — make automated isolation fail so the BPMN error
 *                              escalation path can be demonstrated (defaults false)
 */
public record RaiseIncidentRequest(
        @NotBlank String title,
        @NotBlank String source,
        Boolean forceIsolationFailure) {
}
