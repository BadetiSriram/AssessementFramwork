package com.aaseya.camunda.framework.core.exception;

/**
 * Permanent technical failure (misconfiguration, schema mismatch, corrupt data).
 *
 * <p><strong>Worker behaviour:</strong> when thrown from a {@code BaseWorker.doWork()},
 * the framework dispatches {@code newThrowErrorCommand} with code {@code TECHNICAL_FAILURE}
 * so the BPMN process can route to an incident-handling boundary event rather than
 * exhausting retries.
 *
 * <p><strong>REST controller behaviour:</strong> when thrown from a REST controller, the
 * framework maps this to HTTP {@code 500 Internal Server Error}.
 */
public final class NonRetryableException extends TechnicalException {

    /**
     * Creates a non-retryable exception with an error code and message but no cause.
     *
     * @param errorCode    stable error identifier; non-null, non-blank
     * @param errorMessage human-readable failure description; non-null, non-blank
     * @throws NullPointerException     if {@code errorCode} or {@code errorMessage} is {@code null}
     * @throws IllegalArgumentException if {@code errorCode} or {@code errorMessage} is blank
     */
    public NonRetryableException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    /**
     * Creates a non-retryable exception with an error code, message, and chained cause.
     *
     * @param errorCode    stable error identifier; non-null, non-blank
     * @param errorMessage human-readable failure description; non-null, non-blank
     * @param cause        the underlying exception that triggered this one; may be {@code null}
     * @throws NullPointerException     if {@code errorCode} or {@code errorMessage} is {@code null}
     * @throws IllegalArgumentException if {@code errorCode} or {@code errorMessage} is blank
     */
    public NonRetryableException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }
}
