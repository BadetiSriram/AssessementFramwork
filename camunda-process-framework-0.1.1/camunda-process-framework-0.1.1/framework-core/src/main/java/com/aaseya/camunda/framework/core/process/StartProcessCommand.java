package com.aaseya.camunda.framework.core.process;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable command object used to start a new Camunda process instance.
 * Construct via the static factories {@link #of} or {@link #withVariables} for
 * common cases; the full constructor is available for multi-tenant scenarios.
 *
 * @param bpmnProcessId the BPMN process definition ID (e.g. {@code Process_Booking})
 * @param variables     process-start variables; never {@code null}, may be empty
 * @param businessKey   correlation key carried through every step of the process
 * @param tenantId      optional Camunda tenant ID; {@code null} for single-tenant clusters
 */
public record StartProcessCommand(
        String bpmnProcessId,
        Map<String, Object> variables,
        String businessKey,
        String tenantId
) {

    /** Defensive canonical constructor — ensures variables map is never null. */
    public StartProcessCommand {
        Objects.requireNonNull(bpmnProcessId, "bpmnProcessId must not be null");
        Objects.requireNonNull(businessKey, "businessKey must not be null");
        variables = (variables == null) ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(variables));
    }

    /**
     * Creates a minimal command with no process variables and no tenant constraint.
     *
     * @param bpmnProcessId BPMN process definition ID
     * @param businessKey   business correlation key
     * @return a new {@code StartProcessCommand}
     */
    public static StartProcessCommand of(String bpmnProcessId, String businessKey) {
        return new StartProcessCommand(bpmnProcessId, Collections.emptyMap(), businessKey, null);
    }

    /**
     * Creates a command that carries the given variables map, with no tenant constraint.
     *
     * @param bpmnProcessId BPMN process definition ID
     * @param businessKey   business correlation key
     * @param variables     initial process variables
     * @return a new {@code StartProcessCommand}
     */
    public static StartProcessCommand withVariables(
            String bpmnProcessId, String businessKey, Map<String, Object> variables) {
        return new StartProcessCommand(bpmnProcessId, variables, businessKey, null);
    }
}
