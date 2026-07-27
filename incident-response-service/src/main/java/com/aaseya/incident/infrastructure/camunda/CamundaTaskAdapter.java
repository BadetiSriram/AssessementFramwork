package com.aaseya.incident.infrastructure.camunda;

import com.aaseya.incident.web.dto.UserTaskView;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Adapter over the Camunda 8.9 {@link CamundaClient} for user-task operations (search + complete)
 * against the Orchestration Cluster API v2.
 *
 * <p>Lives in {@code infrastructure.camunda} — the only place allowed to import
 * {@code io.camunda.client} (ArchUnit rule #2). Returns plain DTO records.
 */
@Component
public class CamundaTaskAdapter {

    private final CamundaClient camunda;

    public CamundaTaskAdapter(CamundaClient camunda) {
        this.camunda = camunda;
    }

    /** Active (CREATED) user tasks for a process instance. Retries for the search index lag. */
    public List<UserTaskView> searchActiveUserTasks(long processInstanceKey) {
        for (int attempt = 0; attempt < 10; attempt++) {
            List<UserTaskView> tasks = camunda.newUserTaskSearchRequest()
                    .filter(f -> {
                        f.processInstanceKey(processInstanceKey);
                        f.state(UserTaskState.CREATED);
                    })
                    .send().join().items().stream()
                    .map(t -> new UserTaskView(
                            t.getUserTaskKey(),
                            t.getElementId(),
                            t.getName(),
                            t.getAssignee(),
                            t.getState() == null ? null : t.getState().name()))
                    .toList();
            if (!tasks.isEmpty()) {
                return tasks;
            }
            sleep(400);
        }
        return List.of();
    }

    /** Complete a user task with the given output variables (as if submitted from Tasklist). */
    public void completeUserTask(long userTaskKey, Map<String, Object> variables) {
        camunda.newCompleteUserTaskCommand(userTaskKey)
                .variables(variables == null ? Map.of() : variables)
                .send().join();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
