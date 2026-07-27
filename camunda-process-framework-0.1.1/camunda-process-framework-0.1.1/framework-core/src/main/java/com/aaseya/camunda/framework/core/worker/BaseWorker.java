package com.aaseya.camunda.framework.core.worker;

import com.aaseya.camunda.framework.core.exception.BusinessException;
import com.aaseya.camunda.framework.core.exception.NonRetryableException;
import com.aaseya.camunda.framework.core.exception.RetryableException;
import com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard;
import com.aaseya.camunda.framework.core.mdc.MdcKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Template-method base class that every job worker in the framework extends.
 * Subclasses implement only {@link #varsType()} and {@link #doWork(Object, ActivatedJob)};
 * the framework handles deserialization, MDC population, idempotency short-circuiting,
 * Camunda command dispatch, and metrics emission.
 *
 * <p>Workers must NOT hold business rules — delegate to the application / domain layer.
 * One worker class per job type; compensation workers live in a sibling class.
 *
 * @param <V> the record type that holds the job's typed input variables
 */
public abstract class BaseWorker<V> {

    private static final Logger log = LoggerFactory.getLogger(BaseWorker.class);

    private static final String METRIC_COMPLETED      = "framework_job_completed_total";
    private static final String METRIC_BUSINESS_ERROR = "framework_job_business_error_total";
    private static final String METRIC_FAILED         = "framework_job_failed_total";
    private static final String METRIC_REPLAYED       = "framework_job_replayed_total";

    private final VariableMapper mapper;
    private final IdempotencyGuard guard;
    private final MeterRegistry meterRegistry;

    /**
     * Constructs the base worker with its framework dependencies.
     *
     * @param mapper        deserializes raw Camunda variables into the typed record {@code V}
     * @param guard         detects and short-circuits replayed jobs
     * @param meterRegistry Micrometer registry for {@code framework_job_*} counters
     */
    protected BaseWorker(VariableMapper mapper, IdempotencyGuard guard, MeterRegistry meterRegistry) {
        this.mapper = mapper;
        this.guard = guard;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Returns the class token for the variable record type {@code V}.
     * Used by {@link VariableMapper} to deserialize the job payload.
     *
     * @return class of {@code V}
     */
    protected abstract Class<V> varsType();

    /**
     * Performs the actual job work.  The framework calls this after variables are bound
     * and idempotency is verified.  Return a {@link WorkResult} to signal the outcome;
     * throw a {@link RuntimeException} only for transient technical failures (Camunda
     * will decrement retries and eventually raise an incident).
     *
     * @param vars typed variable record for this job
     * @param job  raw Camunda job, available for metadata (element ID, process key, etc.)
     * @return the job outcome
     */
    protected abstract WorkResult doWork(V vars, ActivatedJob job);

    /**
     * Validates the bound input variables before {@link #doWork} runs.
     * Default implementation is a no-op. Subclasses may throw
     * {@link BusinessException} to signal validation failure; the framework will
     * translate the throw into a BPMN error event with the exception's error code.
     *
     * @param vars bound input variables
     */
    protected void validate(V vars) {
        // no-op default
    }

    /**
     * Transforms the successful {@link WorkResult.Completed} into the actual output
     * variable map dispatched to Camunda. Default returns {@code result.variables()}
     * unchanged. Override to inject computed fields or filter secrets before dispatch.
     *
     * @param result the completed work result
     * @return output variables to send with {@code newCompleteCommand}
     */
    protected Map<String, Object> mapResponse(WorkResult.Completed result) {
        return result.variables();
    }

    /**
     * Handles an unexpected exception that was not a {@link BusinessException},
     * {@link RetryableException}, or {@link NonRetryableException}. Default rethrows
     * so Camunda's retry model applies. Override to translate to a
     * {@link WorkResult}, for example to demote a specific driver exception to a
     * business error.
     *
     * @param ex  the unexpected exception
     * @param job the activated job (for metadata: element ID, key, etc.)
     * @return a replacement {@link WorkResult} to dispatch instead, or {@code null}
     *         to rethrow (which will decrement Camunda retries)
     */
    protected WorkResult handleException(Exception ex, ActivatedJob job) {
        return null;  // signal: framework should rethrow
    }

    /**
     * Returns the job type string used as the {@code type} label on framework metrics.
     * Defaults to the job's actual type at runtime; subclasses may override for clarity.
     *
     * @param job the activated job
     * @return job type string
     */
    protected String workerType(ActivatedJob job) {
        return job.getType();
    }

    /**
     * Framework-owned execution skeleton — annotate your subclass method with
     * {@code @JobWorker(type = "...", autoComplete = false)} and delegate to this method.
     * Declared {@code final} to prevent accidental override of framework concerns.
     *
     * @param client Camunda job client for completing or throwing error commands
     * @param job    the activated job received from Camunda
     */
    public final void execute(JobClient client, ActivatedJob job) {
        String type = workerType(job);

        // 1. Deserialize variables — primary path uses getVariablesAsType, fallback parses JSON string
        V vars;
        try {
            vars = job.getVariablesAsType(varsType());
        } catch (Exception primary) {
            try {
                vars = mapper.map(job.getVariables(), varsType());
            } catch (Exception fallback) {
                log.error("Variable binding failed for job type={} key={}: {}",
                        type, job.getKey(), primary.getMessage(), primary);
                throw new RuntimeException("Variable binding failed: " + primary.getMessage(), primary);
            }
        }

        // 2. Push MDC context
        MDC.put(MdcKeys.PROCESS_INSTANCE_KEY, String.valueOf(job.getProcessInstanceKey()));
        MDC.put(MdcKeys.ELEMENT_ID, job.getElementId());
        MDC.put(MdcKeys.JOB_TYPE, type);
        MDC.put(MdcKeys.JOB_KEY, String.valueOf(job.getKey()));
        MDC.put(MdcKeys.WORKER_NAME, this.getClass().getSimpleName());

        String tenantId = job.getTenantId();
        if (tenantId != null) {
            MDC.put(MdcKeys.TENANT_ID, tenantId);
        }

        // businessKey: attempt to read from variables map by convention
        String businessKey = extractBusinessKey(job);
        if (businessKey != null) {
            MDC.put(MdcKeys.BUSINESS_KEY, businessKey);
        }

        try {
            // 3. Idempotency check
            if (businessKey != null && guard.check(businessKey, job.getElementId())) {
                log.info("Replayed job detected type={} elementId={} businessKey={} — completing silently",
                        type, job.getElementId(), businessKey);
                client.newCompleteCommand(job).send().join();
                meterRegistry.counter(METRIC_REPLAYED, "type", type).increment();
                return;
            }

            // 4a. Validation hook
            validate(vars);

            // 4b. Execute worker logic
            WorkResult result = doWork(vars, job);

            // 5. Dispatch Camunda command based on result type
            if (result instanceof WorkResult.Completed completed) {
                Map<String, Object> outVars = mapResponse(completed);
                client.newCompleteCommand(job).variables(outVars).send().join();
                meterRegistry.counter(METRIC_COMPLETED, "type", type).increment();
                if (businessKey != null) {
                    guard.record(businessKey, job.getElementId(), null);
                }
                log.info("Job completed type={} key={}", type, job.getKey());

            } else if (result instanceof WorkResult.BusinessError businessError) {
                String code = businessError.errorCode();
                String msg  = businessError.errorMessage();
                client.newThrowErrorCommand(job)
                        .errorCode(code)
                        .errorMessage(msg)
                        .send()
                        .join();
                meterRegistry.counter(METRIC_BUSINESS_ERROR, "type", type, "code", code).increment();
                log.info("Business error thrown type={} key={} code={}", type, job.getKey(), code);

            } else if (result instanceof WorkResult.Compensated) {
                client.newCompleteCommand(job).send().join();
                meterRegistry.counter(METRIC_COMPLETED, "type", type).increment();
                log.info("Compensation job completed type={} key={}", type, job.getKey());
            }

        } catch (BusinessException be) {
            client.newThrowErrorCommand(job)
                    .errorCode(be.errorCode())
                    .errorMessage(be.errorMessage())
                    .send()
                    .join();
            meterRegistry.counter(METRIC_BUSINESS_ERROR, "type", type, "code", be.errorCode()).increment();
            log.info("Business exception translated type={} key={} code={}",
                    type, job.getKey(), be.errorCode());
        } catch (RetryableException re) {
            meterRegistry.counter(METRIC_FAILED, "type", type).increment();
            log.warn("Retryable technical failure type={} key={} code={}: {}",
                    type, job.getKey(), re.errorCode(), re.errorMessage(), re);
            throw re;  // Camunda decrements retries
        } catch (NonRetryableException nre) {
            client.newThrowErrorCommand(job)
                    .errorCode("TECHNICAL_FAILURE")
                    .errorMessage(nre.errorMessage())
                    .send()
                    .join();
            meterRegistry.counter(METRIC_FAILED, "type", type, "code", "TECHNICAL_FAILURE").increment();
            log.error("Non-retryable technical failure type={} key={} code={}: {}",
                    type, job.getKey(), nre.errorCode(), nre.errorMessage(), nre);
        } catch (RuntimeException ex) {
            WorkResult fallback = handleException(ex, job);
            if (fallback == null) {
                meterRegistry.counter(METRIC_FAILED, "type", type).increment();
                log.error("Unhandled technical failure type={} key={}: {}",
                        type, job.getKey(), ex.getMessage(), ex);
                throw ex;  // Camunda decrements retries
            }
            // Dispatch the fallback WorkResult — do NOT call execute() again;
            // inline the dispatch so we don't re-run validate/doWork.
            if (fallback instanceof WorkResult.Completed completed) {
                Map<String, Object> outVars = mapResponse(completed);
                client.newCompleteCommand(job).variables(outVars).send().join();
                meterRegistry.counter(METRIC_COMPLETED, "type", type).increment();
            } else if (fallback instanceof WorkResult.BusinessError businessError) {
                client.newThrowErrorCommand(job)
                        .errorCode(businessError.errorCode())
                        .errorMessage(businessError.errorMessage())
                        .send()
                        .join();
                meterRegistry.counter(METRIC_BUSINESS_ERROR, "type", type,
                        "code", businessError.errorCode()).increment();
            } else if (fallback instanceof WorkResult.Compensated) {
                client.newCompleteCommand(job).send().join();
                meterRegistry.counter(METRIC_COMPLETED, "type", type).increment();
            }
        } finally {
            // 6. Clear MDC keys pushed in step 2 (in push order)
            MDC.remove(MdcKeys.PROCESS_INSTANCE_KEY);
            MDC.remove(MdcKeys.ELEMENT_ID);
            MDC.remove(MdcKeys.JOB_TYPE);
            MDC.remove(MdcKeys.JOB_KEY);
            MDC.remove(MdcKeys.WORKER_NAME);
            MDC.remove(MdcKeys.TENANT_ID);
            MDC.remove(MdcKeys.BUSINESS_KEY);
        }
    }

    /**
     * Extracts the {@code businessKey} variable from the job's variables JSON by convention.
     * Returns {@code null} if the variable is absent so the caller can skip idempotency.
     */
    private String extractBusinessKey(ActivatedJob job) {
        try {
            String variables = job.getVariables();
            if (variables == null || variables.isBlank() || "{}".equals(variables.strip())) {
                return null;
            }
            // Parse only the businessKey field from the JSON without full record binding
            JsonNode root = new ObjectMapper().readTree(variables);
            JsonNode node = root.get("businessKey");
            return (node != null && !node.isNull()) ? node.asText() : null;
        } catch (Exception ex) {
            log.debug("Could not extract businessKey from job variables: {}", ex.getMessage());
            return null;
        }
    }
}
