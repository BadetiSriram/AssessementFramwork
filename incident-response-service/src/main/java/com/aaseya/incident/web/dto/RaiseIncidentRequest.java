package com.aaseya.incident.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * Body for POST /incidents, i.e. what a SIEM would send us.
 *
 * <p>Only title and source are required. The four triage signals are optional inputs to the
 * classification and regulatory DMNs (assetCriticality is LOW/MEDIUM/HIGH/CRITICAL); leave them
 * out and you get a P1 that needs regulatory notification.
 *
 * <p>The rest are test hooks so every boundary event in the model can be demoed on demand instead
 * of waiting hours or pulling the network out:
 * <ul>
 *   <li>forceIsolationFailure - error boundary on Isolate Systems</li>
 *   <li>forceAiFailure - error boundaries on both AI Agent tasks (sends a bogus model name, so the
 *       connector really does fail and the fallback worker really does run)</li>
 *   <li>slaDuration - overrides the severity-derived CISO review SLA timer, e.g. "PT20S"</li>
 *   <li>regulatoryDeadline - overrides the 72h notification timer, e.g. "PT20S"</li>
 *   <li>responseActions - which ad-hoc actions the commander activates; element ids from
 *       Task_BlockIp, Task_RevokeCredentials, Task_DeployPatch</li>
 * </ul>
 *
 * <p>The last three are validated here rather than left to Zeebe on purpose. A duration Zeebe can't
 * parse, or an element id that isn't in the ad-hoc sub-process, doesn't fail the request - it fails
 * the process instance minutes later, as an incident in Operate on a token nobody is watching. A
 * 400 at the boundary is the difference between a typo and a stuck incident.
 */
public record RaiseIncidentRequest(
        @NotBlank String title,
        @NotBlank String source,
        Boolean forceIsolationFailure,
        Boolean attackConfirmed,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL",
                message = "must be one of LOW, MEDIUM, HIGH, CRITICAL")
        String assetCriticality,
        Boolean dataExposed,
        @PositiveOrZero Integer recordCount,
        Boolean forceAiFailure,
        @Pattern(regexp = ISO_8601_DURATION, message = ISO_8601_MESSAGE)
        String slaDuration,
        @Pattern(regexp = ISO_8601_DURATION, message = ISO_8601_MESSAGE)
        String regulatoryDeadline,
        List<@Pattern(regexp = "Task_BlockIp|Task_RevokeCredentials|Task_DeployPatch",
                message = "must be one of Task_BlockIp, Task_RevokeCredentials, Task_DeployPatch")
                String> responseActions) {

    /** ISO-8601 duration, the format the BPMN timer expressions need. At least one unit required. */
    static final String ISO_8601_DURATION =
            "P(?=[0-9T])(\\d+Y)?(\\d+M)?(\\d+W)?(\\d+D)?(T(?=\\d)(\\d+H)?(\\d+M)?(\\d+(\\.\\d+)?S)?)?";

    static final String ISO_8601_MESSAGE = "must be an ISO-8601 duration, e.g. PT20S, PT4H, P3D";
}
