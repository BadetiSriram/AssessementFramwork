package com.aaseya.incident.application;

import com.aaseya.camunda.framework.core.exception.BusinessException;
import com.aaseya.incident.domain.Incident;
import com.aaseya.incident.domain.IncidentTaskOutcome;
import com.aaseya.incident.infrastructure.camunda.CamundaTaskAdapter;
import com.aaseya.incident.repository.IncidentRepository;
import com.aaseya.incident.repository.IncidentTaskOutcomeRepository;
import com.aaseya.incident.web.dto.UserTaskView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Human tasks for an incident: list what's open, complete one, keep a copy of what was submitted.
 *
 * <p>Completing through here rather than straight through Tasklist is what gives us the outcome
 * rows; the demo can still use Tasklist if it wants to.
 */
@Service
public class IncidentTaskService {

    private final IncidentRepository incidentRepository;
    private final CamundaTaskAdapter taskAdapter;
    private final IncidentTaskOutcomeRepository outcomeRepository;
    private final ObjectMapper objectMapper;

    public IncidentTaskService(IncidentRepository incidentRepository,
                               CamundaTaskAdapter taskAdapter,
                               IncidentTaskOutcomeRepository outcomeRepository,
                               ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.taskAdapter = taskAdapter;
        this.outcomeRepository = outcomeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<UserTaskView> listActiveTasks(UUID incidentId) {
        return taskAdapter.searchActiveUserTasks(processInstanceKey(incidentId));
    }

    /** Look up the task first so we can record its name, then complete it. */
    @Transactional
    public UserTaskView completeTask(UUID incidentId, long userTaskKey,
                                     String completedBy, Map<String, Object> variables) {
        long pik = processInstanceKey(incidentId);
        UserTaskView info = taskAdapter.searchActiveUserTasks(pik).stream()
                .filter(t -> t.userTaskKey() == userTaskKey)
                .findFirst()
                .orElse(null);
        taskAdapter.completeUserTask(userTaskKey, variables);
        outcomeRepository.save(IncidentTaskOutcome.of(
                incidentId,
                userTaskKey,
                info == null ? null : info.elementId(),
                info == null ? null : info.name(),
                completedBy,
                toJson(variables)));
        return info;
    }

    /** Convenience for the single-task phases. Ambiguous during the parallel phase, by design. */
    @Transactional
    public UserTaskView completeActiveTask(UUID incidentId, String completedBy, Map<String, Object> variables) {
        List<UserTaskView> tasks = taskAdapter.searchActiveUserTasks(processInstanceKey(incidentId));
        if (tasks.isEmpty()) {
            throw new BusinessException("NO_ACTIVE_TASK", "No active user task for incident " + incidentId);
        }
        if (tasks.size() > 1) {
            throw new BusinessException("AMBIGUOUS_TASK",
                    "Incident " + incidentId + " has " + tasks.size() + " active tasks, complete by userTaskKey");
        }
        return completeTask(incidentId, tasks.get(0).userTaskKey(), completedBy, variables);
    }

    @Transactional(readOnly = true)
    public List<IncidentTaskOutcome> listOutcomes(UUID incidentId) {
        return outcomeRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    private long processInstanceKey(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new BusinessException("INCIDENT_NOT_FOUND", "No incident with id " + incidentId));
        Long pik = incident.getProcessInstanceKey();
        if (pik == null) {
            throw new BusinessException("PROCESS_NOT_STARTED",
                    "Incident " + incidentId + " has no process instance yet");
        }
        return pik;
    }

    private String toJson(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(variables == null ? Map.of() : variables);
        } catch (Exception e) {
            return "{}";
        }
    }
}
