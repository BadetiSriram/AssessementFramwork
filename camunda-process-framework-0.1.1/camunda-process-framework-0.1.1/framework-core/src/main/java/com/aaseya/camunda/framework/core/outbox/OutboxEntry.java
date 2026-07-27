package com.aaseya.camunda.framework.core.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable projection of a single row in the {@code process_outbox} table.
 * Used internally by {@link JdbcOutboxRelay} when reading undispatched rows during polling.
 *
 * @param id            surrogate primary key
 * @param aggregateType name of the domain aggregate (e.g. {@code Booking})
 * @param aggregateId   string form of the aggregate's primary key
 * @param kind          whether this row starts a new process or correlates a message
 * @param payload       JSON-serialized command payload
 * @param createdAt     when the outbox row was written (within the domain transaction)
 * @param dispatchedAt  when the row was successfully dispatched; {@code null} if pending
 */
public record OutboxEntry(
        UUID id,
        String aggregateType,
        String aggregateId,
        Kind kind,
        String payload,
        Instant createdAt,
        Instant dispatchedAt
) {

    /**
     * Discriminator for the outbox row, determining which {@link ProcessService} method
     * the poller invokes.
     */
    public enum Kind {
        /** Row represents a {@link com.aaseya.camunda.framework.core.process.StartProcessCommand}. */
        START,
        /** Row represents a {@link com.aaseya.camunda.framework.core.process.CorrelateMessageCommand}. */
        MESSAGE
    }
}
