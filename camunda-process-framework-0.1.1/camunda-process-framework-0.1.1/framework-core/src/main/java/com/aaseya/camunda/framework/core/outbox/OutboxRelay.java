package com.aaseya.camunda.framework.core.outbox;

import com.aaseya.camunda.framework.core.process.CorrelateMessageCommand;
import com.aaseya.camunda.framework.core.process.StartProcessCommand;

/**
 * Port for the transactional outbox pattern.
 * {@code publishStart} / {@code publishCorrelate} are called inside the same database
 * transaction as the domain write, guaranteeing at-least-once delivery.
 * {@code poll} is invoked by a scheduled thread and dispatches pending rows to Camunda.
 *
 * <p>Consuming services must add {@code @EnableScheduling} to their Spring configuration
 * for the poller to run.
 */
public interface OutboxRelay {

    /**
     * Writes a {@link StartProcessCommand} to the outbox within the caller's active transaction.
     *
     * @param cmd the command to persist and later dispatch
     */
    void publishStart(StartProcessCommand cmd);

    /**
     * Writes a {@link CorrelateMessageCommand} to the outbox within the caller's active transaction.
     *
     * @param cmd the command to persist and later dispatch
     */
    void publishCorrelate(CorrelateMessageCommand cmd);

    /**
     * Picks undispatched outbox rows and forwards them to {@link com.aaseya.camunda.framework.core.process.ProcessService}.
     * Marks each row as dispatched on success.  Intended to be called by a {@code @Scheduled} method.
     */
    void poll();
}
