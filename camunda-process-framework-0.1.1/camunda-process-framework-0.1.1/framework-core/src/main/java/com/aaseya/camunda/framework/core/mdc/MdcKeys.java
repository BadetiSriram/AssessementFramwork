package com.aaseya.camunda.framework.core.mdc;

/**
 * Canonical MDC key constants shared across all framework components so that every
 * structured log line carries the same field names regardless of which module emits it.
 */
public final class MdcKeys {

    private MdcKeys() {
        // utility class — no instances
    }

    /** Caller-supplied business identifier (booking ID, order ID, etc.). */
    public static final String BUSINESS_KEY = "businessKey";

    /** Camunda tenant identifier; {@code null} on non-multi-tenant clusters. */
    public static final String TENANT_ID = "tenantId";

    /** Numeric Camunda process instance key, set by the engine on start. */
    public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";

    /** BPMN element ID of the current service task or event. */
    public static final String ELEMENT_ID = "elementId";

    /** Camunda job type string (kebab-case, e.g. {@code validate-order}). */
    public static final String JOB_TYPE = "jobType";

    /** HTTP correlation identifier propagated via {@code X-Correlation-Id} header. */
    public static final String CORRELATION_ID = "correlationId";

    /** Numeric Camunda job key from {@code ActivatedJob.getKey()}. */
    public static final String JOB_KEY = "jobKey";

    /** Simple class name of the concrete {@link com.aaseya.camunda.framework.core.worker.BaseWorker} subclass. */
    public static final String WORKER_NAME = "workerName";
}
