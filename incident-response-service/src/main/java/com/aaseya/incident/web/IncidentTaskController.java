package com.aaseya.incident.web;

import com.aaseya.camunda.framework.starter.web.Response;
import com.aaseya.incident.application.IncidentTaskService;
import com.aaseya.incident.web.dto.CompleteTaskRequest;
import com.aaseya.incident.web.dto.TaskOutcomeView;
import com.aaseya.incident.web.dto.UserTaskView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Human-task API for an incident — list active Tasklist tasks, complete them from the API (the
 * demo can still use Tasklist), and read the recorded outcomes.
 *
 * <pre>
 *   GET  /incidents/{id}/tasks                       active user tasks (containment verification, ...)
 *   POST /incidents/{id}/tasks/{userTaskKey}/complete   complete a specific task + record outcome
 *   POST /incidents/{id}/tasks/complete              complete the single active task + record outcome
 *   GET  /incidents/{id}/tasks/outcomes              recorded outcomes (persisted to Postgres)
 * </pre>
 */
@RestController
@RequestMapping("/incidents/{id}/tasks")
public class IncidentTaskController {

    private final IncidentTaskService taskService;

    public IncidentTaskController(IncidentTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public Response<List<UserTaskView>> listTasks(@PathVariable UUID id) {
        return Response.ok(taskService.listActiveTasks(id));
    }

    @PostMapping("/{userTaskKey}/complete")
    public Response<String> complete(@PathVariable UUID id,
                                     @PathVariable long userTaskKey,
                                     @RequestBody(required = false) CompleteTaskRequest body) {
        UserTaskView info = taskService.completeTask(id, userTaskKey,
                body == null ? null : body.completedBy(),
                body == null ? null : body.variables());
        return Response.ok("Completed user task " + userTaskKey
                + (info == null ? "" : " (" + info.name() + ")") + " for incident " + id);
    }

    @PostMapping("/complete")
    public Response<String> completeActive(@PathVariable UUID id,
                                           @RequestBody(required = false) CompleteTaskRequest body) {
        UserTaskView info = taskService.completeActiveTask(id,
                body == null ? null : body.completedBy(),
                body == null ? null : body.variables());
        return Response.ok("Completed active user task"
                + (info == null ? "" : " (" + info.name() + ")") + " for incident " + id);
    }

    @GetMapping("/outcomes")
    public Response<List<TaskOutcomeView>> outcomes(@PathVariable UUID id) {
        return Response.ok(taskService.listOutcomes(id).stream().map(TaskOutcomeView::from).toList());
    }
}
