package com.aaseya.incident.application;

import com.aaseya.camunda.framework.core.exception.BusinessException;
import com.aaseya.camunda.framework.core.process.ProcessService;
import com.aaseya.camunda.framework.core.process.StartProcessCommand;
import com.aaseya.incident.domain.Incident;
import com.aaseya.incident.domain.IncidentSeverity;
import com.aaseya.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * @param title                 short incident title
     * @param source                alert source, e.g. "SIEM"
     * @param forceIsolationFailure test hook: make automated isolation fail (→ BPMN error escalation)
     * @return the persisted incident
     */
    @Transactional
    public Incident raiseIncident(String title, String source, boolean forceIsolationFailure) {
        Incident incident = Incident.raise(title, source);
        incidentRepository.save(incident);

        processService.start(StartProcessCommand.withVariables(
                PROCESS_ID,
                incident.getBusinessKey(),
                Map.of(
                        "businessKey", incident.getBusinessKey(),
                        "incidentId", incident.getId().toString(),
                        "title", incident.getTitle(),
                        "source", incident.getSource(),
                        "forceIsolationFailure", forceIsolationFailure,
                        // Ad-hoc response actions the incident commander activates. Defaulted so
                        // the flow runs end-to-end; at runtime the commander selects these via the
                        // ad-hoc sub-process API as findings emerge (block-ip / revoke-credentials
                        // / deploy-patch — inner element IDs of the ad-hoc sub-process).
                        "responseActions", List.of("Task_BlockIp", "Task_RevokeCredentials"))));

        return incident;
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
