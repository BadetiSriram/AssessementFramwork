package com.aaseya.camunda.framework.starter.camunda;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the Camunda process framework worker settings.
 *
 * <p>All properties are bound under the {@code framework.camunda} prefix. Example:
 *
 * <pre>{@code
 * framework:
 *   camunda:
 *     multi-tenant: false
 *     worker:
 *       max-jobs-active: 64
 *       poll-interval: PT15S
 *       retry-backoff: PT10S
 *       default-retries: 5
 * }</pre>
 *
 * <p>Values not explicitly set in the application configuration fall back to the built-in
 * defaults defined in {@link Worker}.
 */
@ConfigurationProperties(prefix = "framework.camunda")
public class FrameworkCamundaProperties {

    /**
     * Whether this deployment targets a multi-tenant Camunda cluster.
     *
     * <p>When {@code true}, the {@link com.aaseya.camunda.framework.core.process.CamundaProcessService}
     * will forward the {@code tenantId} from each command to the Camunda engine.
     * When {@code false} (the default), {@code tenantId} values are silently dropped even if
     * present on the command, keeping single-tenant clusters free of the tenant routing overhead.
     */
    private boolean multiTenant = false;

    private Worker worker = new Worker();

    /**
     * Returns whether this service is running against a multi-tenant Camunda cluster.
     *
     * @return {@code true} if multi-tenant routing is enabled
     */
    public boolean isMultiTenant() {
        return multiTenant;
    }

    /**
     * Sets whether this service should forward tenant IDs to the Camunda engine.
     *
     * @param multiTenant {@code true} to enable multi-tenant routing
     */
    public void setMultiTenant(boolean multiTenant) {
        this.multiTenant = multiTenant;
    }

    /**
     * Returns the worker configuration properties.
     *
     * @return the {@link Worker} settings; never {@code null}
     */
    public Worker getWorker() {
        return worker;
    }

    /**
     * Sets the worker configuration properties.
     *
     * @param worker the worker settings to apply; must not be {@code null}
     */
    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    /**
     * Worker-level tuning parameters for Camunda job polling and retry behaviour.
     *
     * <p>These settings apply framework-wide to all job workers registered in the
     * application. Individual workers may override them via the
     * {@code @JobWorker} annotation attributes where the Camunda Spring client
     * supports per-worker overrides.
     */
    public static class Worker {

        /**
         * Maximum number of jobs that a single worker instance may hold activated
         * (in-flight) at any one time.
         *
         * <p>Increasing this value improves throughput on fast, CPU-bound jobs but
         * raises memory pressure proportionally to payload size. Keep it at or below
         * the HPA replica-count ceiling times the per-replica job budget.
         */
        private int maxJobsActive = 32;

        /**
         * How often the Camunda client polls the broker for new jobs when the
         * worker's in-flight count is below {@link #maxJobsActive}.
         *
         * <p>Shorter intervals reduce end-to-end latency at the cost of more
         * network round-trips. The Kubernetes {@code preStop} sleep must be at
         * least as long as this interval to allow graceful drain on pod shutdown.
         */
        private Duration pollInterval = Duration.ofSeconds(30);

        /**
         * Base back-off duration applied between job-execution retry attempts when
         * the job handler throws an unclassified (technical) exception.
         *
         * <p>The Camunda engine applies this value as the retry-back-off hint; the
         * actual scheduling is managed by the engine, not the client.
         */
        private Duration retryBackoff = Duration.ofSeconds(5);

        /**
         * Default number of retries assigned to jobs whose BPMN task definition
         * does not specify an explicit retry count.
         *
         * <p>Business errors (thrown via {@code newThrowErrorCommand}) bypass the
         * retry mechanism entirely — this value applies only to technical failures
         * (unclassified exceptions that decrement the retry counter).
         */
        private int defaultRetries = 3;

        /**
         * Creates a {@code Worker} instance with all default values applied.
         */
        public Worker() {
        }

        /**
         * Returns the maximum number of jobs that may be activated simultaneously.
         *
         * @return the max-jobs-active limit; positive integer
         */
        public int getMaxJobsActive() {
            return maxJobsActive;
        }

        /**
         * Sets the maximum number of jobs that may be activated simultaneously.
         *
         * @param maxJobsActive a positive integer
         */
        public void setMaxJobsActive(int maxJobsActive) {
            this.maxJobsActive = maxJobsActive;
        }

        /**
         * Returns the polling interval between job-activation requests.
         *
         * @return the poll interval; never {@code null}
         */
        public Duration getPollInterval() {
            return pollInterval;
        }

        /**
         * Sets the polling interval between job-activation requests.
         *
         * @param pollInterval a non-null, positive duration
         */
        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        /**
         * Returns the back-off duration applied between technical retry attempts.
         *
         * @return the retry back-off; never {@code null}
         */
        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        /**
         * Sets the back-off duration applied between technical retry attempts.
         *
         * @param retryBackoff a non-null, non-negative duration
         */
        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
        }

        /**
         * Returns the default retry count for jobs without an explicit BPMN retry setting.
         *
         * @return the default retry count; non-negative integer
         */
        public int getDefaultRetries() {
            return defaultRetries;
        }

        /**
         * Sets the default retry count for jobs without an explicit BPMN retry setting.
         *
         * @param defaultRetries a non-negative integer
         */
        public void setDefaultRetries(int defaultRetries) {
            this.defaultRetries = defaultRetries;
        }
    }
}
