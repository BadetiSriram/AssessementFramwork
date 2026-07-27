package com.aaseya.camunda.framework.core.process;

import com.aaseya.camunda.framework.core.mdc.MdcKeys;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Primary adapter that fulfils {@link ProcessService} by delegating to the Camunda 8.9
 * REST client.  All engine interactions are isolated here so application and domain layers
 * remain free of {@code io.camunda.client.*} imports.
 *
 * <p>User-task search calls are wrapped in a bounded exponential-backoff retry because the
 * Camunda search index is eventually consistent and may lag task activation by several
 * hundred milliseconds.
 */
public class CamundaProcessService implements ProcessService {

    private static final Logger log = LoggerFactory.getLogger(CamundaProcessService.class);

    /** Initial retry delay for user-task search (ms). */
    private static final long RETRY_INITIAL_DELAY_MS = 200L;

    /** Backoff multiplier applied after each failed attempt. */
    private static final int RETRY_FACTOR = 2;

    /** Maximum number of search attempts (including the first). */
    private static final int RETRY_MAX_ATTEMPTS = 5;

    private final CamundaClient camundaClient;
    private final boolean multiTenant;

    /**
     * Constructs the service with the injected Camunda client bean, defaulting to
     * single-tenant mode.  Existing callers and tests that use this constructor
     * continue to work unchanged.
     *
     * @param camundaClient the {@code CamundaClient} managed by the Spring context
     */
    public CamundaProcessService(CamundaClient camundaClient) {
        this(camundaClient, false);
    }

    /**
     * Constructs the service with explicit multi-tenant configuration.
     * Used by the auto-configuration to wire the deployment-level flag from
     * {@code framework.camunda.multi-tenant}.
     *
     * @param camundaClient the {@code CamundaClient} managed by the Spring context
     * @param multiTenant   {@code true} if this deployment targets a multi-tenant cluster
     *                      and tenant IDs should be forwarded to the Camunda engine
     */
    public CamundaProcessService(CamundaClient camundaClient, boolean multiTenant) {
        this.camundaClient = camundaClient;
        this.multiTenant = multiTenant;
    }

    /**
     * Returns whether this instance was constructed with multi-tenant mode enabled.
     * Exposed for auto-configuration bean-wiring assertions in the starter test suite.
     *
     * @return {@code true} if multi-tenant routing is active
     */
    public boolean isMultiTenant() {
        return multiTenant;
    }

    /** {@inheritDoc} */
    @Override
    public long start(StartProcessCommand cmd) {
        long processInstanceKey;
        if (multiTenant && cmd.tenantId() != null) {
            processInstanceKey = camundaClient.newCreateInstanceCommand()
                    .bpmnProcessId(cmd.bpmnProcessId())
                    .latestVersion()
                    .tenantId(cmd.tenantId())
                    .variables(cmd.variables())
                    .send()
                    .join()
                    .getProcessInstanceKey();
        } else {
            if (!multiTenant && cmd.tenantId() != null) {
                log.debug("Multi-tenant mode is disabled; dropping tenantId='{}' for bpmnProcessId='{}'",
                        cmd.tenantId(), cmd.bpmnProcessId());
            }
            processInstanceKey = camundaClient.newCreateInstanceCommand()
                    .bpmnProcessId(cmd.bpmnProcessId())
                    .latestVersion()
                    .variables(cmd.variables())
                    .send()
                    .join()
                    .getProcessInstanceKey();
        }
        log.info("Started process instance key={} bpmnProcessId={} businessKey={}",
                processInstanceKey, cmd.bpmnProcessId(), cmd.businessKey());
        return processInstanceKey;
    }

    /** {@inheritDoc} */
    @Override
    public void correlate(CorrelateMessageCommand cmd) {
        if (multiTenant && cmd.tenantId() != null) {
            camundaClient.newCorrelateMessageCommand()
                    .messageName(cmd.messageName())
                    .correlationKey(cmd.correlationKey())
                    .tenantId(cmd.tenantId())
                    .variables(cmd.variables())
                    .send()
                    .join();
        } else {
            if (!multiTenant && cmd.tenantId() != null) {
                log.debug("Multi-tenant mode is disabled; dropping tenantId='{}' for messageName='{}'",
                        cmd.tenantId(), cmd.messageName());
            }
            camundaClient.newCorrelateMessageCommand()
                    .messageName(cmd.messageName())
                    .correlationKey(cmd.correlationKey())
                    .variables(cmd.variables())
                    .send()
                    .join();
        }
        log.info("Correlated message={} correlationKey={}", cmd.messageName(), cmd.correlationKey());
    }

    /** {@inheritDoc} */
    @Override
    public void publish(PublishMessageCommand cmd) {
        try {
            // messageName() → Step2; correlationKey() / withoutCorrelationKey() → Step3.
            // Step3 carries variables(), timeToLive(), messageId(), and send().
            PublishMessageCommandStep1.PublishMessageCommandStep3 step3;
            if (cmd.correlationKey() != null) {
                step3 = camundaClient.newPublishMessageCommand()
                        .messageName(cmd.messageName())
                        .correlationKey(cmd.correlationKey());
            } else {
                step3 = camundaClient.newPublishMessageCommand()
                        .messageName(cmd.messageName())
                        .withoutCorrelationKey();
            }

            // Chain optional fields only when values are present.
            if (cmd.variables() != null && !cmd.variables().isEmpty()) {
                step3 = step3.variables(cmd.variables());
            }
            if (cmd.timeToLive() != null) {
                step3 = step3.timeToLive(cmd.timeToLive());
            }
            if (cmd.messageId() != null) {
                step3 = step3.messageId(cmd.messageId());
            }

            // Multi-tenant: mirror the pattern used by correlate() — forward tenantId only
            // when multi-tenant mode is active.  PublishMessageCommand carries no tenantId
            // field (unlike CorrelateMessageCommand); tenant routing for published messages
            // is not yet exposed by this command object.  When multi-tenant support for
            // publish is required, add a tenantId field to PublishMessageCommand and call
            // step3.tenantId(cmd.tenantId()) here, guarded by the same
            // (multiTenant && cmd.tenantId() != null) predicate used in correlate().

            step3.send().join();

        } catch (Exception e) {
            throw new ProcessServiceException(
                    "Failed to publish message '" + cmd.messageName() + "': " + e.getMessage(), e);
        }
        log.info("Published message={} correlationKey={}", cmd.messageName(), cmd.correlationKey());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Long> findActiveUserTaskKey(long processInstanceKey) {
        long delayMs = RETRY_INITIAL_DELAY_MS;
        for (int attempt = 1; attempt <= RETRY_MAX_ATTEMPTS; attempt++) {
            var response = camundaClient.newUserTaskSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey).state(UserTaskState.CREATED))
                    .page(p -> p.limit(1))
                    .send()
                    .join();

            List<UserTask> items = response.items();
            if (items != null && !items.isEmpty()) {
                long taskKey = items.get(0).getUserTaskKey();
                log.debug("Found user task key={} for processInstanceKey={}", taskKey, processInstanceKey);
                return Optional.of(taskKey);
            }

            if (attempt < RETRY_MAX_ATTEMPTS) {
                log.debug("User task not yet indexed for processInstanceKey={}, attempt {}/{}, retrying in {}ms",
                        processInstanceKey, attempt, RETRY_MAX_ATTEMPTS, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for user task search, processInstanceKey={}", processInstanceKey);
                    return Optional.empty();
                }
                delayMs *= RETRY_FACTOR;
            }
        }
        log.info("No active user task found for processInstanceKey={} after {} attempts",
                processInstanceKey, RETRY_MAX_ATTEMPTS);
        return Optional.empty();
    }

    /** {@inheritDoc} */
    @Override
    public void completeActiveUserTask(long processInstanceKey, Map<String, Object> vars) {
        long taskKey = findActiveUserTaskKey(processInstanceKey)
                .orElseThrow(() -> new ProcessServiceException(
                        "No active user task found for processInstanceKey=" + processInstanceKey
                        + " after " + RETRY_MAX_ATTEMPTS + " attempts"));

        camundaClient.newCompleteUserTaskCommand(taskKey)
                .variables(vars)
                .send()
                .join();
        log.info("Completed user task key={} for processInstanceKey={}", taskKey, processInstanceKey);
    }

    /** {@inheritDoc} */
    @Override
    public void cancel(long processInstanceKey, String reason) {
        MDC.put(MdcKeys.PROCESS_INSTANCE_KEY, String.valueOf(processInstanceKey));
        try {
            camundaClient.newCancelInstanceCommand(processInstanceKey)
                    .send()
                    .join();
            log.info("Cancelled process instance key={} reason={}", processInstanceKey, reason);
        } finally {
            MDC.remove(MdcKeys.PROCESS_INSTANCE_KEY);
        }
    }
}
