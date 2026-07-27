package com.aaseya.orders.web;

import com.aaseya.camunda.framework.starter.web.Response;
import com.aaseya.orders.application.OrchestrationService;
import com.aaseya.orders.web.dto.AssignRequest;
import com.aaseya.orders.web.dto.ProcessInstanceView;
import com.aaseya.orders.web.dto.StartProcessRequest;
import com.aaseya.orders.web.dto.UserTaskView;
import com.aaseya.orders.web.dto.VariableView;
import com.aaseya.orders.web.dto.VariablesRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sample endpoints exercising the Camunda 8.9 <b>Orchestration Cluster API v2</b> for the
 * operations that previously required the separate Operate and Tasklist services.
 *
 * <p>Operate-style (process instances + variables):
 * <pre>
 *   GET  /orchestration/process-instances?processDefinitionId=order-fulfillment&limit=20
 *   GET  /orchestration/process-instances/{key}
 *   GET  /orchestration/process-instances/{key}/variables
 *   PUT  /orchestration/process-instances/{key}/variables      body: {"variables": {...}}
 * </pre>
 * Tasklist-style (user tasks):
 * <pre>
 *   GET  /orchestration/user-tasks?processInstanceKey={key}&assignee={user}&limit=20
 *   POST /orchestration/user-tasks/{key}/complete              body: {"variables": {...}}
 *   POST /orchestration/user-tasks/{key}/assign                body: {"assignee": "..."}
 * </pre>
 *
 * <p>Follows the framework layering (web → application → infrastructure.camunda): this
 * controller never imports {@code io.camunda.client}, is not {@code @Transactional}, and
 * returns DTO records inside the framework {@link Response} envelope.
 */
@RestController
@RequestMapping("/orchestration")
public class OrchestrationController {

    private final OrchestrationService orchestration;

    public OrchestrationController(OrchestrationService orchestration) {
        this.orchestration = orchestration;
    }

    /** Start a process instance by BPMN process id (e.g. "order-approval") to reach a user task. */
    @PostMapping("/process-instances")
    public Response<Long> startInstance(@RequestBody StartProcessRequest body) {
        return Response.ok(orchestration.startProcess(body.processDefinitionId(), body.variables()));
    }

    // ----- Operate-style -----

    @GetMapping("/process-instances")
    public Response<List<ProcessInstanceView>> searchInstances(
            @RequestParam(required = false) String processDefinitionId,
            @RequestParam(defaultValue = "20") int limit) {
        return Response.ok(orchestration.searchProcessInstances(processDefinitionId, limit));
    }

    @GetMapping("/process-instances/{key}")
    public Response<ProcessInstanceView> getInstance(@PathVariable long key) {
        return Response.ok(orchestration.getProcessInstance(key));
    }

    @GetMapping("/process-instances/{key}/variables")
    public Response<List<VariableView>> getVariables(@PathVariable long key) {
        return Response.ok(orchestration.getProcessInstanceVariables(key));
    }

    @PutMapping("/process-instances/{key}/variables")
    public Response<List<VariableView>> setVariables(
            @PathVariable long key,
            @RequestBody VariablesRequest body) {
        return Response.ok(orchestration.setProcessVariables(key, body.variables()));
    }

    // ----- Tasklist-style -----

    @GetMapping("/user-tasks")
    public Response<List<UserTaskView>> searchTasks(
            @RequestParam(required = false) Long processInstanceKey,
            @RequestParam(required = false) String assignee,
            @RequestParam(defaultValue = "20") int limit) {
        return Response.ok(orchestration.searchUserTasks(processInstanceKey, assignee, limit));
    }

    @PostMapping("/user-tasks/{key}/complete")
    public Response<String> completeTask(
            @PathVariable long key,
            @RequestBody(required = false) VariablesRequest body) {
        orchestration.completeUserTask(key, body == null ? null : body.variables());
        return Response.ok("User task " + key + " completed");
    }

    @PostMapping("/user-tasks/{key}/assign")
    public Response<String> assignTask(
            @PathVariable long key,
            @RequestBody AssignRequest body) {
        orchestration.assignUserTask(key, body.assignee());
        return Response.ok("User task " + key + " assigned to " + body.assignee());
    }

    /**
     * Complete the active user task of a process instance by its <b>process instance key</b>
     * (as you have it from Operate) — no need to look up the task key yourself.
     */
    @PostMapping("/process-instances/{key}/complete-user-task")
    public Response<String> completeActiveTask(
            @PathVariable long key,
            @RequestBody(required = false) VariablesRequest body) {
        long userTaskKey = orchestration.completeActiveUserTask(key, body == null ? null : body.variables());
        return Response.ok("Completed active user task " + userTaskKey + " for process instance " + key);
    }
}
