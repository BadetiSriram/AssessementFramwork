package com.aaseya.camunda.framework.core.exception;

/**
 * Domain-level business error that the framework routes to the BPMN error lane
 * (via {@code newThrowErrorCommand}) rather than the technical incident lane.
 * Carry a short, stable {@code errorCode} that matches the BPMN error definition.
 */
public class BusinessError extends RuntimeException {

    private final String errorCode;

    /**
     * Creates a business error with the BPMN error code and a human-readable message.
     *
     * @param errorCode stable BPMN error code (e.g. {@code VALIDATION_FAILED})
     * @param message   human-readable description for logs and incident notes
     */
    public BusinessError(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** Returns the BPMN error code used to match the boundary event. */
    public String getErrorCode() {
        return errorCode;
    }
}
