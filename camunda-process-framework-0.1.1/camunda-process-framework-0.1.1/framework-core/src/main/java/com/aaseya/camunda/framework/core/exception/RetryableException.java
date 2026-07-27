package com.aaseya.camunda.framework.core.exception;

/**
 * Transient technical failure (network timeout, temporary lock, database contention).
 *
 * <p><strong>Worker behaviour:</strong> when thrown from a {@code BaseWorker.doWork()},
 * the framework rethrows so Camunda decrements the job's retry count and eventually
 * raises an incident.
 *
 * <p><strong>REST controller behaviour:</strong> when thrown from a REST controller, the
 * framework maps this to HTTP {@code 503 Service Unavailable}.
 */
public final class RetryableException extends TechnicalException {

    /**
     * Creates a retryable exception with an error code and message but no cause.
     *
     * @param errorCode    stable error identifier; non-null, non-blank
     * @param errorMessage human-readable failure description; non-null, non-blank
     * @throws NullPointerException     if {@code errorCode} or {@code errorMessage} is {@code null}
     * @throws IllegalArgumentException if {@code errorCode} or {@code errorMessage} is blank
     */
    public RetryableException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    /**
     * Creates a retryable exception with an error code, message, and chained cause.
     *
     * @param errorCode    stable error identifier; non-null, non-blank
     * @param errorMessage human-readable failure description; non-null, non-blank
     * @param cause        the underlying exception that triggered this one; may be {@code null}
     * @throws NullPointerException     if {@code errorCode} or {@code errorMessage} is {@code null}
     * @throws IllegalArgumentException if {@code errorCode} or {@code errorMessage} is blank
     */
    public RetryableException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }
}
