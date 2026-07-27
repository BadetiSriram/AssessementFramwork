package com.aaseya.camunda.framework.core.exception;

import java.util.Objects;

/**
 * Abstract root of the framework exception hierarchy.
 *
 * <p>All framework-managed exceptions extend this class. Concrete leaf types fall into two
 * branches:
 * <ul>
 *   <li>{@link BusinessException} — expected failures that belong to the business flow.</li>
 *   <li>{@link TechnicalException} and its subtypes — infrastructure or integration
 *       failures that are orthogonal to the business flow.</li>
 * </ul>
 *
 * <p>Every instance carries a stable, upper-case {@code errorCode} that is safe to
 * propagate across process boundaries (BPMN error codes, API error payloads, metrics
 * labels) and a human-readable {@code errorMessage} for logs and incident notes.
 */
public abstract class FrameworkException extends RuntimeException {

    /**
     * Stable, non-blank identifier for this error category.
     * Used as the BPMN error code when routing to boundary events and as a metrics label.
     */
    private final String errorCode;

    /**
     * Human-readable description of the failure.
     * Equals {@link #getMessage()} on this exception; stored separately to avoid
     * subclasses having to cast or call {@code super.getMessage()}.
     */
    private final String errorMessage;

    /**
     * Creates a framework exception with an error code and message but no cause.
     *
     * @param errorCode    stable, non-null, non-blank error identifier
     * @param errorMessage human-readable failure description; non-null, non-blank
     * @throws NullPointerException     if {@code errorCode} or {@code errorMessage} is {@code null}
     * @throws IllegalArgumentException if {@code errorCode} or {@code errorMessage} is blank
     */
    protected FrameworkException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = requireNonBlank(errorCode, "errorCode");
        this.errorMessage = requireNonBlank(errorMessage, "errorMessage");
    }

    /**
     * Creates a framework exception with an error code, message, and chained cause.
     *
     * @param errorCode    stable, non-null, non-blank error identifier
     * @param errorMessage human-readable failure description; non-null, non-blank
     * @param cause        the underlying exception that triggered this one; may be {@code null}
     * @throws NullPointerException     if {@code errorCode} or {@code errorMessage} is {@code null}
     * @throws IllegalArgumentException if {@code errorCode} or {@code errorMessage} is blank
     */
    protected FrameworkException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = requireNonBlank(errorCode, "errorCode");
        this.errorMessage = requireNonBlank(errorMessage, "errorMessage");
    }

    /**
     * Returns the stable error code for this exception.
     *
     * @return non-null, non-blank error code
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * Returns the human-readable error message for this exception.
     *
     * @return non-null, non-blank error message
     */
    public String errorMessage() {
        return errorMessage;
    }

    // ---- private helpers ----

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
