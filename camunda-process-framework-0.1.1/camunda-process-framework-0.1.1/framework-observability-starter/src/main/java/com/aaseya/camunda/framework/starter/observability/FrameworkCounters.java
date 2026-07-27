package com.aaseya.camunda.framework.starter.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Convenience helper for recording domain-level business counters with
 * consistent naming according to the framework engineering standards.
 *
 * <p>Counter names follow the pattern {@code <domain>_<state>_total} where
 * {@code <domain>} is the value supplied at construction time. This matches the
 * Prometheus naming convention and allows uniform dashboarding across services.
 *
 * <p>Services instantiate this class once per domain, typically in a
 * {@code @Configuration} class:
 *
 * <pre>{@code
 * // Example — not auto-configured; services own instantiation.
 * @Bean
 * public FrameworkCounters orderCounters(MeterRegistry registry,
 *                                        FrameworkObservabilityProperties props) {
 *     String prefix = props.getMetrics().getBusinessCounterPrefix();
 *     return new FrameworkCounters(registry, prefix.isBlank() ? "orders" : prefix);
 * }
 * }</pre>
 *
 * <p>No {@code FrameworkCounters} bean is registered automatically by the
 * starter because the domain name is application-specific. Services choose
 * their own domain prefix.
 */
public class FrameworkCounters {

    private final MeterRegistry registry;
    private final String domainName;

    /**
     * Creates a new helper bound to the given registry and domain prefix.
     *
     * @param registry   the Micrometer registry to record counters into; must not be null
     * @param domainName the domain prefix used in counter names (e.g. {@code orders});
     *                   must not be blank
     */
    public FrameworkCounters(MeterRegistry registry, String domainName) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        if (domainName == null || domainName.isBlank()) {
            throw new IllegalArgumentException("domainName must not be blank");
        }
        this.registry = registry;
        this.domainName = domainName;
    }

    /**
     * Increments the {@code <domain>_created_total} counter.
     */
    public void created() {
        increment("created");
    }

    /**
     * Increments the {@code <domain>_approved_total} counter.
     */
    public void approved() {
        increment("approved");
    }

    /**
     * Increments the {@code <domain>_rejected_total} counter.
     */
    public void rejected() {
        increment("rejected");
    }

    /**
     * Increments the {@code <domain>_completed_total} counter.
     */
    public void completed() {
        increment("completed");
    }

    /**
     * Increments the {@code <domain>_compensated_total} counter.
     */
    public void compensated() {
        increment("compensated");
    }

    /**
     * Increments the {@code <domain>_failed_total} counter.
     */
    public void failed() {
        increment("failed");
    }

    /**
     * Increments the counter for an arbitrary state with no additional tags.
     *
     * @param state the state label appended to the domain prefix (e.g. {@code "cancelled"})
     */
    public void increment(String state) {
        increment(state, Tags.empty());
    }

    /**
     * Increments the counter for an arbitrary state with additional tags.
     *
     * <p>Counter name: {@code <domain>_<state>_total}.
     *
     * @param state the state label appended to the domain prefix
     * @param tags  additional Micrometer tags; must not be null (use {@link Tags#empty()} for none)
     */
    public void increment(String state, Tags tags) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state must not be blank");
        }
        String counterName = domainName + "_" + state + "_total";
        Counter.builder(counterName)
                .tags(tags)
                .register(registry)
                .increment();
    }

    /**
     * Returns the domain name prefix used for all counters created by this instance.
     *
     * @return domain name; never blank
     */
    public String getDomainName() {
        return domainName;
    }
}
