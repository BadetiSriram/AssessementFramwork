package com.aaseya.camunda.framework.core.idempotency;

/**
 * Port for idempotent-receiver logic used by {@link com.aaseya.camunda.framework.core.worker.BaseWorker}.
 * Prevents duplicate side-effects when Camunda re-delivers a job after a crash or timeout.
 * Implementations must be safe to call from concurrent worker threads.
 */
public interface IdempotencyGuard {

    /**
     * Returns {@code true} if the {@code (businessKey, elementId)} tuple has already been
     * recorded, indicating that this job execution is a replay and should be short-circuited.
     *
     * @param businessKey domain-level business key carried through the process instance
     * @param elementId   BPMN element ID of the current service task
     * @return {@code true} if the execution was previously completed
     */
    boolean check(String businessKey, String elementId);

    /**
     * Records a completed execution so that subsequent retries are detected as replays.
     * Implementations must be idempotent: inserting the same tuple twice must not throw.
     *
     * @param businessKey domain-level business key
     * @param elementId   BPMN element ID of the completed task
     * @param resultHash  optional content hash of the result variables; may be {@code null}
     */
    void record(String businessKey, String elementId, String resultHash);
}
