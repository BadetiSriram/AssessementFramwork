package com.aaseya.camunda.framework.core.exception;

/**
 * Signals an infrastructure or integration failure that is not part of the business flow.
 *
 * <p>This class is abstract. Concrete subclasses {@link RetryableException} and
 * {@link NonRetryableException} further specify the retry behaviour that the framework
 * applies when the exception escapes a {@code BaseWorker.doWork()} invocation.
 */
public abstract class TechnicalException extends FrameworkException {

    /**
     * Creates a technical exception with an error code and message but no cause.
     *
     * @param errorCode    stable error identifier; non-null, non-blank
     * @param errorMessage human-readable failure description; non-null, non-blank
     * @throws NullPointerException     if {@code errorCode} or {@code errorMessage} is {@code null}
     * @throws IllegalArgumentException if {@code errorCode} or {@code errorMessage} is blank
     */
    protected TechnicalException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    /**
     * Creates a technical exception with an error code, message, and chained cause.
     *
     * @param errorCode    stable error identifier; non-null, non-blank
     * @param errorMessage human-readable failure description; non-null, non-blank
     * @param cause        the underlying exception that triggered this one; may be {@code null}
     * @throws NullPointerException     if {@code errorCode} or {@code errorMessage} is {@code null}
     * @throws IllegalArgumentException if {@code errorCode} or {@code errorMessage} is blank
     */
    protected TechnicalException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }
}
