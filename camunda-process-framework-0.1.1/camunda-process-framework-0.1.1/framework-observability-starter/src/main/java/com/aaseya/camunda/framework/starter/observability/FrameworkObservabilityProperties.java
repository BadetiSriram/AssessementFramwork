package com.aaseya.camunda.framework.starter.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the framework observability starter.
 *
 * <p>All properties live under the {@code framework.observability} prefix.
 * Defaults are chosen so that a service that adds this starter requires zero
 * explicit configuration for a working baseline.
 *
 * <pre>{@code
 * framework:
 *   observability:
 *     mdc:
 *       enabled: true
 *       header-name: X-Correlation-Id
 *       generate-if-absent: true
 *     metrics:
 *       business-counter-prefix: orders   # set to your domain name
 * }</pre>
 */
@ConfigurationProperties(prefix = "framework.observability")
public class FrameworkObservabilityProperties {

    private final Mdc mdc = new Mdc();
    private final Metrics metrics = new Metrics();

    public Mdc getMdc() {
        return mdc;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * Configuration for the MDC correlation-id servlet filter.
     */
    public static class Mdc {

        /** Whether to register the {@code MdcCorrelationFilter}. */
        private boolean enabled = true;

        /**
         * Name of the HTTP request/response header that carries the correlation id.
         * Defaults to the de-facto industry standard {@code X-Correlation-Id}.
         */
        private String headerName = "X-Correlation-Id";

        /**
         * When {@code true} and the inbound request does not carry a correlation id
         * header, a random UUID is generated and stored in the MDC for the duration
         * of the request.
         */
        private boolean generateIfAbsent = true;

        /**
         * Name of the HTTP request header that carries the tenant identifier.
         * When present the value is placed in the MDC under {@code MdcKeys.TENANT_ID}.
         */
        private String tenantIdHeaderName = "X-Tenant-Id";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public boolean isGenerateIfAbsent() {
            return generateIfAbsent;
        }

        public void setGenerateIfAbsent(boolean generateIfAbsent) {
            this.generateIfAbsent = generateIfAbsent;
        }

        public String getTenantIdHeaderName() {
            return tenantIdHeaderName;
        }

        public void setTenantIdHeaderName(String tenantIdHeaderName) {
            this.tenantIdHeaderName = tenantIdHeaderName;
        }
    }

    /**
     * Configuration for framework-level Micrometer business counters.
     */
    public static class Metrics {

        /**
         * Prefix (domain name) used when constructing business counter names via
         * {@link FrameworkCounters}. Services typically override this with their
         * bounded-context name (e.g. {@code orders}, {@code payments}).
         * When left empty the domain name must be provided at
         * {@code FrameworkCounters} construction time.
         */
        private String businessCounterPrefix = "";

        public String getBusinessCounterPrefix() {
            return businessCounterPrefix;
        }

        public void setBusinessCounterPrefix(String businessCounterPrefix) {
            this.businessCounterPrefix = businessCounterPrefix;
        }
    }
}
