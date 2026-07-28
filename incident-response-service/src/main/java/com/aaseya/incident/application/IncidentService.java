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
 * Ties the {@link Incident} aggregate to the Camunda process. Transactions live here, not on
 * the controller.
 */
@Service
public class IncidentService {

    /** Must match the process id in incident-response.bpmn. */
    static final String PROCESS_ID = "incident-response";

    private final IncidentRepository incidentRepository;
    private final ProcessService processService;

    public IncidentService(IncidentRepository incidentRepository, ProcessService processService) {
        this.incidentRepository = incidentRepository;
        this.processService = processService;
    }

    /**
     * Raise an incident and kick off its response process.
     *
     * <p>The four triage signals feed the classification and regulatory DMNs. Really they should
     * come out of the AI triage step, but letting the caller pass them means we can demo any
     * severity path on demand. Anything left null falls back to a P1 that needs notification.
     */
    @Transactional
    public Incident raiseIncident(String title, String source, boolean forceIsolationFailure,
                                  Boolean attackConfirmed, String assetCriticality,
                                  Boolean dataExposed, Integer recordCount) {
        Incident incident = Incident.raise(title, source);
        // id is already assigned, so save() merges; work with the returned instance
        Incident saved = incidentRepository.save(incident);

        Map<String, Object> variables = new HashMap<>();
        variables.put("businessKey", saved.getBusinessKey());
        variables.put("incidentId", saved.getId().toString());
        variables.put("title", saved.getTitle());
        variables.put("source", saved.getSource());
        variables.put("forceIsolationFailure", forceIsolationFailure);
        // the AI triage connector overwrites this; seed it so it exists even if that step fails
        variables.put("triageReport", "");
        // Which ad-hoc actions to activate. In real life the commander picks these as findings
        // come in; defaulting them keeps the demo running end to end.
        variables.put("responseActions", List.of("Task_BlockIp", "Task_RevokeCredentials"));
        // The AI agent tasks map =triageAgent.context / =reportAgent.context as input, and Zeebe
        // fails the job if the variable doesn't exist yet. Empty maps are enough.
        variables.put("triageAgent", Map.of());
        variables.put("reportAgent", Map.of());
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

    // called from the job workers

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
