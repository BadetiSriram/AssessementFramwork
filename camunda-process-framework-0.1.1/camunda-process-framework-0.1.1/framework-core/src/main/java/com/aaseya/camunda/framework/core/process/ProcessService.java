package com.aaseya.camunda.framework.core.process;

import java.util.Map;
import java.util.Optional;

/**
 * Port (outbound) for all Camunda process-engine interactions.
 * Application-layer services depend on this interface; infrastructure provides the
 * implementation so that domain and application code stay free of engine imports.
 */
public interface ProcessService {

    /**
     * Starts a new process instance and returns its engine-assigned key.
     *
     * @param cmd typed command carrying the BPMN process ID, variables, and business key
     * @return the Camunda process instance key
     */
    long start(StartProcessCommand cmd);

    /**
     * Correlates a message to a waiting process instance, resuming execution.
     *
     * @param cmd typed command carrying the message name, correlation key, and variables
     */
    void correlate(CorrelateMessageCommand cmd);

    /**
     * Publishes a message to the Camunda engine.  Unlike {@link #correlate(CorrelateMessageCommand)},
     * which addresses a specific waiting process instance, {@code publish} broadcasts the
     * message so any subscribed instance may consume it — including the message-start event
     * of a process definition (which correlates against no instance).
     *
     * @param cmd typed command carrying the message name, optional correlation key, variables,
     *            time-to-live, and message ID
     */
    void publish(PublishMessageCommand cmd);

    /**
     * Completes the active Zeebe user task for the given process instance, setting the
     * supplied output variables.  Retries internally because the search index is
     * eventually consistent.
     *
     * @param processInstanceKey Camunda process instance key
     * @param vars               output variables to write back to the process
     * @throws ProcessServiceException if no active user task is found after all retry
     *         attempts
     */
    void completeActiveUserTask(long processInstanceKey, Map<String, Object> vars);

    /**
     * Searches for the active Zeebe user task associated with the given process instance.
     * Returns {@link Optional#empty()} if no task is found after bounded retries.
     *
     * @param processInstanceKey Camunda process instance key
     * @return the user task key, or empty if not yet available
     */
    Optional<Long> findActiveUserTaskKey(long processInstanceKey);

    /**
     * Cancels a running process instance with a human-readable reason that appears in
     * the Camunda Operate audit trail.
     *
     * @param processInstanceKey Camunda process instance key
     * @param reason             short description of why the instance is being cancelled
     */
    void cancel(long processInstanceKey, String reason);
}
