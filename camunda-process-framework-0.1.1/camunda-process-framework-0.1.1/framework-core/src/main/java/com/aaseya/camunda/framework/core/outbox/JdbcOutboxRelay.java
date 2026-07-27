package com.aaseya.camunda.framework.core.outbox;

import com.aaseya.camunda.framework.core.process.CorrelateMessageCommand;
import com.aaseya.camunda.framework.core.process.ProcessService;
import com.aaseya.camunda.framework.core.process.StartProcessCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JDBC-backed {@link OutboxRelay} that persists commands in the {@code process_outbox}
 * table and dispatches them via a scheduled poll.
 *
 * <p>Consuming services must add {@code @EnableScheduling} to their Spring configuration
 * for the {@link #poll()} method to be invoked automatically.
 *
 * <p>Expected DDL (Flyway migration in consuming service):
 * <pre>{@code
 * CREATE TABLE process_outbox (
 *   id             UUID PRIMARY KEY,
 *   aggregate_type VARCHAR NOT NULL,
 *   aggregate_id   VARCHAR NOT NULL,
 *   kind           VARCHAR NOT NULL,
 *   payload        TEXT    NOT NULL,
 *   created_at     TIMESTAMP DEFAULT current_timestamp,
 *   dispatched_at  TIMESTAMP NULL
 * );
 * CREATE INDEX idx_outbox_undispatched ON process_outbox (created_at) WHERE dispatched_at IS NULL;
 * }</pre>
 *
 * <p>The {@code payload} column is declared as {@code TEXT} rather than {@code JSONB} so that
 * the same Flyway migration works on both Postgres (production) and H2 (local profile).
 * Production deployments that require native JSON operators may migrate the column to
 * {@code jsonb} in a follow-up migration.
 */
public class JdbcOutboxRelay implements OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(JdbcOutboxRelay.class);

    private static final String SQL_INSERT =
            "INSERT INTO process_outbox(id, aggregate_type, aggregate_id, kind, payload) "
            + "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_POLL =
            "SELECT id, aggregate_type, aggregate_id, kind, payload, created_at "
            + "FROM process_outbox "
            + "WHERE dispatched_at IS NULL "
            + "ORDER BY created_at "
            + "LIMIT 100 "
            + "FOR UPDATE SKIP LOCKED";

    private static final String SQL_MARK_DISPATCHED =
            "UPDATE process_outbox SET dispatched_at = now() WHERE id = ?";

    private final JdbcTemplate jdbc;
    private final ProcessService processService;
    private final ObjectMapper mapper;

    /**
     * Creates the relay backed by the given JDBC template, process service, and mapper.
     *
     * @param jdbc           Spring JDBC template connected to the service database
     * @param processService port used to dispatch commands to Camunda
     * @param mapper         Jackson mapper for serializing/deserializing command payloads
     */
    public JdbcOutboxRelay(JdbcTemplate jdbc, ProcessService processService, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.processService = processService;
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public void publishStart(StartProcessCommand cmd) {
        String payload = serialize(cmd);
        jdbc.update(SQL_INSERT,
                UUID.randomUUID(),
                aggregateTypeOf(cmd.bpmnProcessId()),
                cmd.businessKey(),
                OutboxEntry.Kind.START.name(),
                payload);
        log.debug("Outbox row written kind=START bpmnProcessId={} businessKey={}",
                cmd.bpmnProcessId(), cmd.businessKey());
    }

    /** {@inheritDoc} */
    @Override
    public void publishCorrelate(CorrelateMessageCommand cmd) {
        String payload = serialize(cmd);
        jdbc.update(SQL_INSERT,
                UUID.randomUUID(),
                aggregateTypeOf(cmd.messageName()),
                cmd.correlationKey(),
                OutboxEntry.Kind.MESSAGE.name(),
                payload);
        log.debug("Outbox row written kind=MESSAGE messageName={} correlationKey={}",
                cmd.messageName(), cmd.correlationKey());
    }

    /**
     * Polls for undispatched outbox rows, dispatches each to Camunda, and marks them
     * dispatched.  Uses {@code FOR UPDATE SKIP LOCKED} so that multiple replicas do not
     * compete on the same row.
     *
     * <p>Annotated with {@code @Scheduled} — consuming service must enable scheduling via
     * {@code @EnableScheduling} on its Spring configuration class.
     */
    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${framework.outbox.poll-interval:PT2S}")
    public void poll() {
        List<OutboxEntry> rows = jdbc.query(SQL_POLL, this::mapRow);
        if (rows.isEmpty()) {
            return;
        }
        log.debug("Outbox poll found {} undispatched rows", rows.size());

        for (OutboxEntry entry : rows) {
            try {
                dispatch(entry);
                jdbc.update(SQL_MARK_DISPATCHED, entry.id());
            } catch (Exception ex) {
                log.error("Failed to dispatch outbox entry id={} kind={}: {}",
                        entry.id(), entry.kind(), ex.getMessage(), ex);
                // row stays undispatched; next poll will retry
            }
        }
    }

    // ---- private helpers ----

    private void dispatch(OutboxEntry entry) throws Exception {
        if (entry.kind() == OutboxEntry.Kind.START) {
            StartProcessCommand cmd = mapper.readValue(entry.payload(), StartProcessCommand.class);
            processService.start(cmd);
        } else {
            CorrelateMessageCommand cmd = mapper.readValue(entry.payload(), CorrelateMessageCommand.class);
            processService.correlate(cmd);
        }
    }

    private String serialize(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize outbox payload: " + e.getMessage(), e);
        }
    }

    private String aggregateTypeOf(String hint) {
        // Derive a short aggregate type label from the BPMN id or message name
        return hint != null ? hint : "unknown";
    }

    private OutboxEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");
        return new OutboxEntry(
                UUID.fromString(rs.getString("id")),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                OutboxEntry.Kind.valueOf(rs.getString("kind")),
                rs.getString("payload"),
                createdTs != null ? createdTs.toInstant() : Instant.now(),
                null
        );
    }
}
