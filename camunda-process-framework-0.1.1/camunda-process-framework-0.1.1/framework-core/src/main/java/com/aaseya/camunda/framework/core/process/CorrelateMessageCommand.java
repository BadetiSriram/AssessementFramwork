package com.aaseya.camunda.framework.core.process;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable command object used to correlate a Camunda message to a waiting process instance.
 * Per Camunda 8.9 API, {@code correlationKey} must always be a {@link String} — use
 * {@link String#valueOf} when your domain key is numeric.
 *
 * @param messageName    BPMN message name (e.g. {@code PaymentReceived})
 * @param correlationKey string form of the business correlation key
 * @param variables      variables to attach to the correlated message; never {@code null}
 * @param tenantId       optional Camunda tenant ID; {@code null} for single-tenant clusters
 */
public record CorrelateMessageCommand(
        String messageName,
        String correlationKey,
        Map<String, Object> variables,
        String tenantId
) {

    /** Defensive canonical constructor — ensures variables map is never null. */
    public CorrelateMessageCommand {
        Objects.requireNonNull(messageName, "messageName must not be null");
        Objects.requireNonNull(correlationKey, "correlationKey must not be null");
        variables = (variables == null) ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(variables));
    }
}
