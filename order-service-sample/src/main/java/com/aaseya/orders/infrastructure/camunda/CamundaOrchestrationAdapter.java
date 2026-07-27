package com.aaseya.orders.infrastructure.camunda;

import com.aaseya.orders.web.dto.ProcessInstanceView;
import com.aaseya.orders.web.dto.UserTaskView;
import com.aaseya.orders.web.dto.VariableView;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Thin adapter over the Camunda 8.9 {@link CamundaClient} exposing the operations that used
 * to live behind the separate <b>Operate</b> and <b>Tasklist</b> REST APIs. In Camunda 8.9
 * these were consolidated into the single <b>Orchestration Cluster REST API v2</b>, and the
 * Java client speaks it directly — so there is no separate Operate/Tasklist client or Swagger
 * base-URL to configure any more.
 *
 * <p>This class lives in {@code infrastructure.camunda} because it is the ONLY layer allowed
 * to import {@code io.camunda.client} (ArchUnit rule #2). It maps every engine type to a plain
 * DTO record so the application and web layers never see an {@code io.camunda} type.
 *
 * <p>Operate-style: {@link #searchProcessInstances}, {@link #getProcessInstance},
 * {@link #getProcessInstanceVariables}, {@link #setProcessVariables}.
 * <br>Tasklist-style: {@link #searchUserTasks}, {@link #completeUserTask}, {@link #assignUserTask}.
 */
@Component
public class CamundaOrchestrationAdapter {

    private final CamundaClient camunda;

    public CamundaOrchestrationAdapter(CamundaClient camunda) {
        this.camunda = camunda;
    }

    // ----------------------------------------------------------------------
    // Start a process instance (handy for reaching a user task to test)
    // ----------------------------------------------------------------------

    /** Start a new instance of the latest version of {@code processDefinitionId}. */
    public long startProcess(String processDefinitionId, Map<String, Object> variables) {
        return camunda.newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionId)
                .latestVersion()
                .variables(variables == null ? Map.of() : variables)
                .send().join()
                .getProcessInstanceKey();
    }

    // ----------------------------------------------------------------------
    // Operate-style: query / mutate process instances and their variables
    // ----------------------------------------------------------------------

    /** "Get the variables of a process instance" — the classic Operate lookup. */
    public List<VariableView> getProcessInstanceVariables(long processInstanceKey) {
        return camunda.newVariableSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send().join().items().stream()
                .map(v -> new VariableView(v.getName(), v.getValue(), v.getScopeKey()))
                .toList();
    }

    /** "Get a process instance by key". Returns {@code null} if it is not found. */
    public ProcessInstanceView getProcessInstance(long processInstanceKey) {
        return camunda.newProcessInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send().join().items().stream()
                .findFirst()
                .map(CamundaOrchestrationAdapter::toView)
                .orElse(null);
    }

    /** "Search process instances", optionally narrowed to one process definition id. */
    public List<ProcessInstanceView> searchProcessInstances(String processDefinitionId, int limit) {
        return camunda.newProcessInstanceSearchRequest()
                .filter(f -> {
                    if (processDefinitionId != null && !processDefinitionId.isBlank()) {
                        f.processDefinitionId(processDefinitionId);
                    }
                })
                .page(p -> p.limit(limit))
                .send().join().items().stream()
                .map(CamundaOrchestrationAdapter::toView)
                .toList();
    }

    /** Set / merge variables on a running instance scope (Operate-style mutation). */
    public void setProcessVariables(long processInstanceKey, Map<String, Object> variables) {
        camunda.newSetVariablesCommand(processInstanceKey)
                .variables(variables == null ? Map.of() : variables)
                .send().join();
    }

    // ----------------------------------------------------------------------
    // Tasklist-style: query and act on user (human) tasks
    // ----------------------------------------------------------------------

    /** "Search user tasks", optionally by process instance and/or assignee. */
    public List<UserTaskView> searchUserTasks(Long processInstanceKey, String assignee, int limit) {
        return camunda.newUserTaskSearchRequest()
                .filter(f -> {
                    if (processInstanceKey != null) {
                        f.processInstanceKey(processInstanceKey);
                    }
                    if (assignee != null && !assignee.isBlank()) {
                        f.assignee(assignee);
                    }
                })
                .page(p -> p.limit(limit))
                .send().join().items().stream()
                .map(t -> new UserTaskView(
                        t.getUserTaskKey(),
                        t.getName(),
                        t.getElementId(),
                        t.getAssignee(),
                        t.getState() == null ? null : t.getState().name()))
                .toList();
    }

    /** "Complete a user task" with output variables — the core Tasklist write operation. */
    public void completeUserTask(long userTaskKey, Map<String, Object> variables) {
        camunda.newCompleteUserTaskCommand(userTaskKey)
                .variables(variables == null ? Map.of() : variables)
                .send().join();
    }

    /** "Assign a user task" to a user. */
    public void assignUserTask(long userTaskKey, String assignee) {
        camunda.newAssignUserTaskCommand(userTaskKey)
                .assignee(assignee)
                .send().join();
    }

    /**
     * Convenience: given only a <b>process instance key</b> (the way you have it from Operate),
     * find the active (CREATED) user task waiting in that instance and complete it with the
     * supplied variables. Returns the completed task key.
     *
     * <p>Because the v2 search API is eventually consistent (exporter lag), this retries the
     * lookup a few times before giving up — so it works even if called immediately after the
     * job worker hands the token to the user task.
     */
    public long completeActiveUserTask(long processInstanceKey, Map<String, Object> variables) {
        Long userTaskKey = findActiveUserTaskKey(processInstanceKey, 10, 500);
        if (userTaskKey == null) {
            throw new IllegalStateException(
                    "No active (CREATED) user task found for process instance " + processInstanceKey);
        }
        completeUserTask(userTaskKey, variables);
        return userTaskKey;
    }

    /** Poll the v2 search API for the first CREATED user task of an instance (retries for lag). */
    private Long findActiveUserTaskKey(long processInstanceKey, int attempts, long delayMs) {
        for (int i = 0; i < attempts; i++) {
            List<UserTask> tasks = camunda.newUserTaskSearchRequest()
                    .filter(f -> {
                        f.processInstanceKey(processInstanceKey);
                        f.state(UserTaskState.CREATED);
                    })
                    .send().join().items();
            if (!tasks.isEmpty()) {
                return tasks.get(0).getUserTaskKey();
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private static ProcessInstanceView toView(ProcessInstance pi) {
        return new ProcessInstanceView(
                pi.getProcessInstanceKey(),
                pi.getProcessDefinitionId(),
                pi.getState() == null ? null : pi.getState().name(),
                pi.getStartDate() == null ? null : pi.getStartDate().toString());
    }
}
