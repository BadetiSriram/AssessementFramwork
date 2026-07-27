package com.aaseya.camunda.framework.core.exception;

/**
 * Thrown by {@link com.aaseya.camunda.framework.core.audit.AuditableEntity#transition}
 * when the requested status transition is not permitted from the entity's current state.
 * Always maps to an HTTP 409 at the API boundary.
 */
public class IllegalStateTransitionException extends RuntimeException {

    private final String fromState;
    private final String toState;

    /**
     * Creates the exception describing which transition was rejected.
     *
     * @param from enum name of the current (source) state
     * @param to   enum name of the rejected (target) state
     */
    public IllegalStateTransitionException(String from, String to) {
        super("Illegal state transition: " + from + " → " + to);
        this.fromState = from;
        this.toState = to;
    }

    /** Returns the source state name. */
    public String getFromState() {
        return fromState;
    }

    /** Returns the rejected target state name. */
    public String getToState() {
        return toState;
    }
}
