package com.aaseya.camunda.framework.core.process;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable command object used to publish a Camunda 8 message to the broker in a
 * fire-and-forget manner.  Unlike {@link CorrelateMessageCommand}, which addresses a
 * specific waiting process instance (and fails if none is found), a published message is
 * placed on the broker's internal message log and may be consumed by:
 *
 * <ul>
 *   <li>Any process instance that has an active intermediate message-catch event or
 *       receive task whose subscription matches {@code messageName} and
 *       {@code correlationKey}.</li>
 *   <li>A process definition with a message start event whose subscription matches
 *       {@code messageName} — in this case {@code correlationKey} is irrelevant and should
 *       be left {@code null} or empty (broadcast form).</li>
 * </ul>
 *
 * <p><b>Nullable fields and Camunda 8.9 semantics:</b>
 * <ul>
 *   <li>{@code correlationKey} — may be {@code null} or empty to publish a <em>broadcast</em>
 *       message.  The broker routes broadcast messages exclusively by name; any instance
 *       with a matching subscription receives a copy.  For targeted delivery, supply the
 *       same string that the process instance stored in its correlation-key variable.</li>
 *   <li>{@code variables} — may be {@code null}; when absent no process variables are
 *       attached to the message.  The {@link CamundaProcessService} implementation skips
 *       the {@code .variables(...)} call entirely for {@code null} or empty maps to avoid
 *       sending an unnecessary empty JSON object.</li>
 *   <li>{@code timeToLive} — may be {@code null}; the broker applies its default message
 *       TTL (typically configured via {@code zeebe.broker.data.snapshotPeriod} / Operate
 *       settings, usually 1 hour).  Provide an explicit value when the message must survive
 *       longer or must expire sooner (e.g. short-lived OTP confirmations).</li>
 *   <li>{@code messageId} — may be {@code null}; the broker generates a unique ID
 *       automatically.  When provided the broker uses it for <em>deduplication</em>: if a
 *       message with the same {@code messageId} is published within the TTL window the
 *       second publication is silently discarded.  This is the primary mechanism for
 *       at-least-once publishers to achieve exactly-once delivery at the Camunda layer.</li>
 * </ul>
 *
 * @param messageName    BPMN message name as declared in the process model
 *                       (e.g. {@code PaymentAuthorisationReceived}); must not be blank
 * @param correlationKey string form of the business key used to route the message to a
 *                       specific waiting process instance; {@code null} or empty for
 *                       broadcast
 * @param variables      key/value process variables to attach to the message; {@code null}
 *                       is treated as "no variables" at dispatch time
 * @param timeToLive     how long the broker should retain the message if no subscriber
 *                       picks it up; {@code null} applies the broker's default TTL
 * @param messageId      deduplication token; {@code null} lets the broker generate one
 */
public record PublishMessageCommand(
        String messageName,
        String correlationKey,
        Map<String, Object> variables,
        Duration timeToLive,
        String messageId
) {

    /**
     * Compact canonical constructor — validates that {@code messageName} is present and
     * non-blank.  All other fields are optional and may be {@code null}.
     *
     * @throws NullPointerException     if {@code messageName} is {@code null}
     * @throws IllegalArgumentException if {@code messageName} is blank
     */
    public PublishMessageCommand {
        Objects.requireNonNull(messageName, "messageName");
        if (messageName.isBlank()) {
            throw new IllegalArgumentException("messageName must not be blank");
        }
        // correlationKey may be null or empty (broadcast messages).
        // variables may be null; treat as empty at dispatch time.
        // timeToLive may be null; broker default applies.
        // messageId may be null; broker generates one (deduplication window still applies if provided).
    }

    /**
     * Convenience factory for the simplest broadcast form: name only, no correlation key,
     * no variables, broker-default TTL, and auto-generated message ID.
     *
     * <p>Suitable for triggering a message-start event or broadcasting to any listening
     * instance without caring about targeted routing or deduplication.
     *
     * @param messageName BPMN message name; must not be blank
     * @return a new {@code PublishMessageCommand} with all optional fields {@code null}
     */
    public static PublishMessageCommand of(String messageName) {
        return new PublishMessageCommand(messageName, null, null, null, null);
    }
}
