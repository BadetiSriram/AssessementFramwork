package com.aaseya.incident.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound body for {@code POST /incidents} (simulates a SIEM alert).
 *
 * <p>The triage signals ({@code attackConfirmed}, {@code assetCriticality}, {@code dataExposed},
 * {@code recordCount}) feed the Incident Classification and Regulatory Notification DMNs. They are
 * optional — in production they would come from the AI triage / enrichment step; here the caller
 * (or SIEM) may supply them to drive a specific classification. When omitted they default to a
 * high-severity P1 incident that requires regulatory notification (preserving the original
 * behaviour).
 *
 * @param title                 short incident title (required)
 * @param source                alert source, e.g. "SIEM" (required)
 * @param forceIsolationFailure test hook — make automated isolation fail so the BPMN error
 *                              escalation path can be demonstrated (defaults false)
 * @param attackConfirmed       triage signal: is the attack confirmed? (default {@code true})
 * @param assetCriticality      triage signal: LOW / MEDIUM / HIGH / CRITICAL (default "HIGH")
 * @param dataExposed           triage signal: was data exposed? (default {@code true})
 * @param recordCount           triage signal: number of records affected (default 25000)
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
