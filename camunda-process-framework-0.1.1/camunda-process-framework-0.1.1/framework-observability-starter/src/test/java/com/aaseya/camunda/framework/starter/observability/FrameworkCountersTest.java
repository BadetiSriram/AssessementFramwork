package com.aaseya.camunda.framework.starter.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FrameworkCounters}.
 */
class FrameworkCountersTest {

    private SimpleMeterRegistry registry;
    private FrameworkCounters counters;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        counters = new FrameworkCounters(registry, "orders");
    }

    /**
     * The convenience {@code created()} method must register a counter with the
     * correct name {@code orders_created_total} and increment it by one.
     */
    @Test
    void createdIncrementsCorrectCounter() {
        counters.created();

        Counter counter = registry.find("orders_created_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    /**
     * The generic {@code increment(String)} method must handle arbitrary state
     * names and form the correct counter name.
     */
    @Test
    void customStateIncrementsCorrectCounter() {
        counters.increment("cancelled");

        Counter counter = registry.find("orders_cancelled_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    /**
     * Tags supplied to {@code increment(String, Tags)} must be attached to the
     * registered counter and the counter name must still follow the convention.
     */
    @Test
    void tagsAreAppliedToCounter() {
        Tags tags = Tags.of("region", "eu");
        counters.increment("completed", tags);

        Counter counter = registry.find("orders_completed_total")
                .tag("region", "eu")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
