package com.aaseya.incident.application;

import com.aaseya.camunda.framework.core.exception.BusinessException;
import com.aaseya.camunda.framework.core.process.ProcessService;
import com.aaseya.camunda.framework.core.process.StartProcessCommand;
import com.aaseya.incident.domain.Incident;
import com.aaseya.incident.domain.IncidentSeverity;
import com.aaseya.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Use-case / orchestration layer for incident response. Coordinates the {@link Incident}
 * aggregate, persistence, and the Camunda process via the framework {@link ProcessService} port.
 * Transactions live here (never on the controller — ArchUnit rule 4).
 */
@Service
public class IncidentService {

    /** BPMN process id — must match the {@code id} of the process in incident-response.bpmn. */
    static final String PROCESS_ID = "incident-response";

    private final IncidentRepository incidentRepository;
    private final ProcessService processService;

    public IncidentService(IncidentRepository incidentRepository, ProcessService processService) {
        this.incidentRepository = incidentRepository;
        this.processService = processService;
    }

    /**
     * Raise an incident (as if a SIEM alert arrived) and start its response process.
     *
     * <p>The triage signals feed the two DMNs (Incident Classification + Regulatory Notification).
     * In production they would come from the AI triage / enrichment step; here the caller may
     * supply them to drive a specific classification. Any {@code null} defaults to a high-severity
     * P1 incident that requires regulatory notification (preserving the original behaviour).
     *
     * @param title                 short incident title
     * @param source                alert source, e.g. "SIEM"
     * @param forceIsolationFailure test hook: make automated isolation fail (→ BPMN error escalation)
     * @param attackConfirmed       triage: is the attack confirmed? (default {@code true})
     * @param assetCriticality      triage: LOW / MEDIUM / HIGH / CRITICAL (default "HIGH")
     * @param dataExposed           triage: was data exposed? (default {@code true})
     * @param recordCount           triage: number of records affected (default 25000)
     * @return the persisted incident
     */
    @Transactional
    public Incident raiseIncident(String title, String source, boolean forceIsolationFailure,
                                  Boolean attackConfirmed, String assetCriticality,
                                  Boolean dataExposed, Integer recordCount) {
        Incident incident = Incident.raise(title, source);
        // save() merges (id is assigned) and returns the managed instance — use it, not `incident`.
        Incident saved = incidentRepository.save(incident);

        Map<String, Object> variables = new HashMap<>();
        variables.put("businessKey", saved.getBusinessKey());
        variables.put("incidentId", saved.getId().toString());
        variables.put("title", saved.getTitle());
        variables.put("source", saved.getSource());
        variables.put("forceIsolationFailure", forceIsolationFailure);
        // Written by the AI triage connector; initialized so it always exists even if the AI step
        // is skipped/fails.
        variables.put("triageReport", "");
        // Ad-hoc response actions the incident commander activates. Defaulted so the flow runs
        // end-to-end; at runtime the commander selects these via the ad-hoc sub-process API as
        // findings emerge (block-ip / revoke-credentials / deploy-patch — inner element IDs).
        variables.put("responseActions", List.of("Task_BlockIp", "Task_RevokeCredentials"));
        // Initialize the AI Agent connector context variables so the tasks' input mappings
        // (=triageAgent.context / =reportAgent.context) never reference an undefined variable.
        variables.put("triageAgent", Map.of());
        variables.put("reportAgent", Map.of());
        // Triage signals consumed by the classification + regulatory DMNs. Request-driven, with
        // sensible defaults so an alert with no signals still classifies as a high-severity P1.
        variables.put("attackConfirmed", attackConfirmed != null ? attackConfirmed : Boolean.TRUE);
        variables.put("assetCriticality",
                assetCriticality != null && !assetCriticality.isBlank() ? assetCriticality : "HIGH");
        variables.put("dataExposed", dataExposed != null ? dataExposed : Boolean.TRUE);
        variables.put("recordCount", recordCount != null ? recordCount : 25000);

        long processInstanceKey = processService.start(
                StartProcessCommand.withVariables(PROCESS_ID, saved.getBusinessKey(), variables));

        saved.setProcessInstanceKey(processInstanceKey);
        return saved;
    }

    @Transactional(readOnly = true)
    public Incident getIncident(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("INCIDENT_NOT_FOUND",
                        "No incident with id " + id));
    }

    // ---- Worker callbacks (invoked from job workers via this application service) ----

    @Transactional
    public void markTriaged(String incidentId) {
        load(incidentId).markTriaged();
    }

    @Transactional
    public void recordClassification(String incidentId, String severity) {
        load(incidentId).classify(IncidentSeverity.valueOf(severity));
    }

    @Transactional
    public void markRecovering(String incidentId) {
        load(incidentId).markRecovering();
    }

    @Transactional
    public void closeIncident(String incidentId) {
        load(incidentId).close();
    }

    @Transactional
    public void autoClose(String incidentId, String reason) {
        load(incidentId).autoClose(reason);
    }

    private Incident load(String incidentId) {
        return incidentRepository.findById(UUID.fromString(incidentId))
                .orElseThrow(() -> new BusinessException("INCIDENT_NOT_FOUND",
                        "No incident with id " + incidentId));
    }
}
