package com.aaseya.camunda.framework.core.process;

/**
 * Thrown by {@link CamundaProcessService} when a process-engine operation cannot be
 * completed — for example, when the target user task is not found after all retry
 * attempts have been exhausted.
 */
public class ProcessServiceException extends RuntimeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message human-readable description of why the operation failed
     */
    public ProcessServiceException(String message) {
        super(message);
    }

    /**
     * Creates a new exception wrapping an underlying cause.
     *
     * @param message human-readable description of why the operation failed
     * @param cause   the underlying exception
     */
    public ProcessServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
