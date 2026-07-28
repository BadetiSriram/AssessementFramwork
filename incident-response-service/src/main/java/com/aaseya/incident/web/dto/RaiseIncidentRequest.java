package com.aaseya.incident.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for POST /incidents, i.e. what a SIEM would send us.
 *
 * <p>Only title and source are required. The four triage signals are optional inputs to the
 * classification and regulatory DMNs (assetCriticality is LOW/MEDIUM/HIGH/CRITICAL); leave them
 * out and you get a P1 that needs regulatory notification. forceIsolationFailure is a test hook
 * for the error-escalation path.
 */
public record RaiseIncidentRequest(
        @NotBlank String title,
        @NotBlank String source,
        Boolean forceIsolationFailure,
        Boolean attackConfirmed,
        String assetCriticality,
        Boolean dataExposed,
        Integer recordCount) {
}
