package com.aaseya.camunda.framework.core.exception;

/**
 * Signals an expected business-flow failure (e.g., invalid state, business-rule violation).
 *
 * <p><strong>Worker behaviour:</strong> when thrown inside a
 * {@code BaseWorker.doWork()}, the framework maps this to a Camunda BPMN error event via
 * {@code newThrowErrorCommand}, using {@link #errorCode()} as the BPMN error code.
 *
 * <p><strong>REST controller behaviour:</strong> when thrown from a REST controller, the
 * framework maps this to HTTP {@code 422 Unprocessable Entity} via the global exception
 * handler.
 */
public final class BusinessException extends FrameworkException {

    /**
     * Creates a business exception with an error code and message but no cause.
     *
     * @param errorCode    stable BPMN error code matched by a boundary event (e.g.
     *                     {@code VALIDATION_FAILED}); non-null, non-blank
     * @param errorMessage human-readable failure description for logs and incident notes;
     *                     non-null, non-blank
     * @throws NullPointerException     if {@code errorCode} or {@code errorMessage} is {@code null}
     * @throws IllegalArgumentException if {@code errorCode} or {@code errorMessage} is blank
     */
    public BusinessException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    /**
     * Creates a business exception with an error code, message, and chained cause.
     *
     * @param errorCode    stable BPMN error code matched by a boundary event; non-null, non-blank
     * @param errorMessage human-readable failure description; non-null, non-blank
     * @param cause        the underlying exception that triggered this one; may be {@code null}
     * @throws NullPointerException     if {@code errorCode} or {@code errorMessage} is {@code null}
     * @throws IllegalArgumentException if {@code errorCode} or {@code errorMessage} is blank
     */
    public BusinessException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }
}
