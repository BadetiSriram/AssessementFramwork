package com.aaseya.camunda.framework.core.worker;

import java.util.Collections;
import java.util.Map;

/**
 * Sealed result type returned by {@link BaseWorker#doWork}.
 * The framework inspects the concrete permit and routes to the correct Camunda command:
 * {@code Completed} → {@code newCompleteCommand}, {@code BusinessError} →
 * {@code newThrowErrorCommand}, {@code Compensated} → {@code newCompleteCommand} (no vars).
 */
public sealed interface WorkResult
        permits WorkResult.Completed, WorkResult.BusinessError, WorkResult.Compensated {

    /**
     * Signals successful job execution; the provided variables are written back to the
     * process scope.
     *
     * @param variables output variables; never {@code null}, may be empty
     */
    record Completed(Map<String, Object> variables) implements WorkResult {
        /** Defensive constructor — wraps the map as an unmodifiable copy. */
        public Completed {
            variables = (variables == null) ? Collections.emptyMap()
                    : Collections.unmodifiableMap(Map.copyOf(variables));
        }
    }

    /**
     * Signals a domain-level failure that should route to the BPMN error boundary event
     * rather than burning the technical retry budget.
     *
     * @param errorCode    stable BPMN error code (e.g. {@code VALIDATION_FAILED})
     * @param errorMessage human-readable detail for Operate / incident notes
     */
    record BusinessError(String errorCode, String errorMessage) implements WorkResult {}

    /**
     * Signals that this worker executed as part of a saga compensation and that no output
     * variables need to be written.
     */
    record Compensated() implements WorkResult {}

    // ---- static factories ----

    /**
     * Creates a {@link Completed} result carrying the given output variables.
     *
     * @param variables output variables to write to the process scope
     * @return a {@code Completed} instance
     */
    static WorkResult completed(Map<String, Object> variables) {
        return new Completed(variables);
    }

    /**
     * Creates a {@link Completed} result with no output variables.
     *
     * @return a {@code Completed} instance with an empty variable map
     */
    static WorkResult completed() {
        return new Completed(Collections.emptyMap());
    }

    /**
     * Creates a {@link BusinessError} result for the BPMN error lane.
     *
     * @param code    stable BPMN error code
     * @param message human-readable description
     * @return a {@code BusinessError} instance
     */
    static WorkResult businessError(String code, String message) {
        return new BusinessError(code, message);
    }

    /**
     * Creates a {@link Compensated} result for use by compensation workers.
     *
     * @return a {@code Compensated} instance
     */
    static WorkResult compensated() {
        return new Compensated();
    }
}
