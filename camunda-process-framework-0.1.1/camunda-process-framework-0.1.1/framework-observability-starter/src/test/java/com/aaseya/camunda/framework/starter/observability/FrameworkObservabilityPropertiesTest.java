package com.aaseya.camunda.framework.starter.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FrameworkObservabilityProperties} default values and binding.
 */
class FrameworkObservabilityPropertiesTest {

    /**
     * All defaults must be as specified: MDC enabled, header name {@code X-Correlation-Id},
     * generate-if-absent true, tenant header {@code X-Tenant-Id},
     * business-counter-prefix empty.
     */
    @Test
    void defaultsAreCorrect() {
        FrameworkObservabilityProperties props = new FrameworkObservabilityProperties();

        assertThat(props.getMdc().isEnabled()).isTrue();
        assertThat(props.getMdc().getHeaderName()).isEqualTo("X-Correlation-Id");
        assertThat(props.getMdc().isGenerateIfAbsent()).isTrue();
        assertThat(props.getMdc().getTenantIdHeaderName()).isEqualTo("X-Tenant-Id");
        assertThat(props.getMetrics().getBusinessCounterPrefix()).isEmpty();
    }

    /**
     * Explicit property values must override the defaults when bound through
     * the standard Spring Boot {@link Binder}.
     */
    @Test
    void explicitValuesOverrideDefaults() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "framework.observability.mdc.enabled", "false",
                "framework.observability.mdc.header-name", "X-Request-Id",
                "framework.observability.mdc.generate-if-absent", "false",
                "framework.observability.mdc.tenant-id-header-name", "X-Org-Id",
                "framework.observability.metrics.business-counter-prefix", "my_domain"
        ));

        FrameworkObservabilityProperties props = new Binder(source)
                .bind("framework.observability", FrameworkObservabilityProperties.class)
                .get();

        assertThat(props.getMdc().isEnabled()).isFalse();
        assertThat(props.getMdc().getHeaderName()).isEqualTo("X-Request-Id");
        assertThat(props.getMdc().isGenerateIfAbsent()).isFalse();
        assertThat(props.getMdc().getTenantIdHeaderName()).isEqualTo("X-Org-Id");
        assertThat(props.getMetrics().getBusinessCounterPrefix()).isEqualTo("my_domain");
    }
}
