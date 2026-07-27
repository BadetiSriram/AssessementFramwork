package com.aaseya.orders.application;

import com.aaseya.orders.infrastructure.camunda.CamundaOrchestrationAdapter;
import com.aaseya.orders.web.dto.ProcessInstanceView;
import com.aaseya.orders.web.dto.UserTaskView;
import com.aaseya.orders.web.dto.VariableView;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Application-layer facade for Camunda 8.9 Operate/Tasklist-style operations.
 *
 * <p>It exists so the web layer can reach these engine queries/commands without touching the
 * {@code infrastructure} layer directly (ArchUnit rule #1: web/workers must not access
 * infrastructure). These are engine read/command operations, so — unlike
 * {@link OrderService#createOrder} — there is no local database transaction here.
 */
@Service
public class OrchestrationService {

    private final CamundaOrchestrationAdapter camunda;

    public OrchestrationService(CamundaOrchestrationAdapter camunda) {
        this.camunda = camunda;
    }

    public long startProcess(String processDefinitionId, Map<String, Object> variables) {
        return camunda.startProcess(processDefinitionId, variables);
    }

    public List<ProcessInstanceView> searchProcessInstances(String processDefinitionId, int limit) {
        return camunda.searchProcessInstances(processDefinitionId, limit);
    }

    public ProcessInstanceView getProcessInstance(long processInstanceKey) {
        return camunda.getProcessInstance(processInstanceKey);
    }

    public List<VariableView> getProcessInstanceVariables(long processInstanceKey) {
        return camunda.getProcessInstanceVariables(processInstanceKey);
    }

    public List<VariableView> setProcessVariables(long processInstanceKey, Map<String, Object> variables) {
        camunda.setProcessVariables(processInstanceKey, variables);
        return camunda.getProcessInstanceVariables(processInstanceKey); // echo the result back
    }

    public List<UserTaskView> searchUserTasks(Long processInstanceKey, String assignee, int limit) {
        return camunda.searchUserTasks(processInstanceKey, assignee, limit);
    }

    public void completeUserTask(long userTaskKey, Map<String, Object> variables) {
        camunda.completeUserTask(userTaskKey, variables);
    }

    /** Find and complete the active user task of a process instance (given its Operate key). */
    public long completeActiveUserTask(long processInstanceKey, Map<String, Object> variables) {
        return camunda.completeActiveUserTask(processInstanceKey, variables);
    }

    public void assignUserTask(long userTaskKey, String assignee) {
        camunda.assignUserTask(userTaskKey, assignee);
    }
}
