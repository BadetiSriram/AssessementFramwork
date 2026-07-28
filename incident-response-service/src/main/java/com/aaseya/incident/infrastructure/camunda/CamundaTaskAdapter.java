package com.aaseya.incident.infrastructure.camunda;

import com.aaseya.incident.web.dto.UserTaskView;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Everything we need from {@link CamundaClient} for user tasks. Kept in one place so nothing
 * outside this package has to import the Camunda client.
 */
@Component
public class CamundaTaskAdapter {

    private final CamundaClient camunda;

    public CamundaTaskAdapter(CamundaClient camunda) {
        this.camunda = camunda;
    }

    /**
     * Tasks currently waiting on this instance.
     *
     * <p>The search index lags behind the engine by a moment, so a task that was just created
     * won't show up on the first call. Polling for ~4s is enough in practice; if it's genuinely
     * empty we return empty rather than blow up.
     */
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
