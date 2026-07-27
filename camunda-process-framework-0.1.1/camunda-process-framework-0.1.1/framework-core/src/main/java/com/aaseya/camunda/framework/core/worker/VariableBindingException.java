package com.aaseya.camunda.framework.core.worker;

/**
 * Thrown by {@link VariableMapper} when a required record component is {@code null}
 * after deserialization, indicating a BPMN variable contract violation.
 * This is a technical failure (creates an incident) rather than a business error.
 */
public class VariableBindingException extends RuntimeException {

    /**
     * Creates the exception with a descriptive message identifying the missing component.
     *
     * @param message description of which component is missing and in which record type
     */
    public VariableBindingException(String message) {
        super(message);
    }

    /**
     * Creates the exception wrapping a lower-level cause (e.g. Jackson parse failure).
     *
     * @param message description of the binding failure
     * @param cause   the underlying exception
     */
    public VariableBindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
