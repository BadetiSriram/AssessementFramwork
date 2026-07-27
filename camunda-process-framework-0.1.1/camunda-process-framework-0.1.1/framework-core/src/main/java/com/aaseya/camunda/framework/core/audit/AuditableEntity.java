package com.aaseya.camunda.framework.core.audit;

import com.aaseya.camunda.framework.core.exception.IllegalStateTransitionException;

import java.util.Set;

/**
 * Abstract base for domain entities that enforce a typed state machine and append an
 * audit trail entry on every status change.  Subclasses supply the permitted-transition
 * map and persistence behaviour; the framework enforces legality and atomicity.
 *
 * @param <S> the enum type that represents the entity's lifecycle states
 */
public abstract class AuditableEntity<S extends Enum<S>> {

    /**
     * Returns the set of states that are legally reachable from {@code from}.
     * Called by {@link #transition} before applying any change; a missing or empty
     * set means the transition is always rejected.
     *
     * @param from the entity's current state
     * @return non-null set of permitted target states
     */
    protected abstract Set<S> allowedTransitions(S from);

    /**
     * Returns the entity's current status.  Subclass reads from its own field or JPA column.
     *
     * @return current lifecycle state; must not be {@code null} after entity creation
     */
    protected abstract S getStatus();

    /**
     * Persists the new status on the entity.  Called by {@link #transition} after
     * legality is verified, before {@link #appendAuditNote}.
     *
     * @param status the new status to apply
     */
    protected abstract void setStatus(S status);

    /**
     * Persists one audit row capturing the transition.  Subclass writes to its audit
     * table / embedded collection; this method is called within the same transaction as
     * the status update.
     *
     * @param note human-readable reason supplied by the caller
     * @param from previous state
     * @param to   new state
     */
    protected abstract void appendAuditNote(String note, S from, S to);

    /**
     * Applies a status transition if it is permitted, then appends an audit note.
     * Throws {@link IllegalStateTransitionException} immediately if the transition is not
     * in the allowed set — callers should map this to a 409 response at the API boundary.
     *
     * @param to   the target state to transition into
     * @param note short human-readable reason (stored in the audit trail)
     * @throws IllegalStateTransitionException if {@code to} is not reachable from the current state
     */
    public void transition(S to, String note) {
        S from = getStatus();
        Set<S> permitted = allowedTransitions(from);
        if (permitted == null || !permitted.contains(to)) {
            throw new IllegalStateTransitionException(
                    from != null ? from.name() : "null",
                    to != null ? to.name() : "null");
        }
        setStatus(to);
        appendAuditNote(note, from, to);
    }
}
