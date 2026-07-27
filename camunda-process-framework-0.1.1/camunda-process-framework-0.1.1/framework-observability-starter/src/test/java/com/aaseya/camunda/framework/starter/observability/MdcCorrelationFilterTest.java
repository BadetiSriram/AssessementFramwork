package com.aaseya.camunda.framework.starter.observability;

import com.aaseya.camunda.framework.core.mdc.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MdcCorrelationFilter}.
 */
class MdcCorrelationFilterTest {

    private FrameworkObservabilityProperties.Mdc config;
    private MdcCorrelationFilter filter;

    @BeforeEach
    void setUp() {
        config = new FrameworkObservabilityProperties.Mdc();
        filter = new MdcCorrelationFilter(config);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    /**
     * When the request carries a correlation id, the same value must be used
     * and echoed back in the response header.
     */
    @Test
    void inboundCorrelationIdIsPreserved() throws ServletException, IOException {
        String existingId = "test-correlation-id-123";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", existingId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedMdc = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedMdc.set(MDC.get(MdcKeys.CORRELATION_ID));

        filter.doFilter(request, response, chain);

        assertThat(capturedMdc.get()).isEqualTo(existingId);
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(existingId);
    }

    /**
     * When no correlation id header is present and {@code generateIfAbsent=true},
     * a UUID must be generated and placed in the MDC.
     */
    @Test
    void correlationIdGeneratedWhenAbsent() throws ServletException, IOException {
        config.setGenerateIfAbsent(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedMdc = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedMdc.set(MDC.get(MdcKeys.CORRELATION_ID));

        filter.doFilter(request, response, chain);

        assertThat(capturedMdc.get())
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(capturedMdc.get());
    }

    /**
     * When the {@code X-Tenant-Id} header is present, its value must be propagated
     * into the MDC under {@link MdcKeys#TENANT_ID}.
     */
    @Test
    void tenantIdPropagatedFromHeader() throws ServletException, IOException {
        String tenantId = "tenant-acme";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", tenantId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedTenant = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedTenant.set(MDC.get(MdcKeys.TENANT_ID));

        filter.doFilter(request, response, chain);

        assertThat(capturedTenant.get()).isEqualTo(tenantId);
    }

    /**
     * Both MDC keys must be removed after the filter chain completes so values
     * cannot leak across requests on pooled threads.
     */
    @Test
    void mdcClearedAfterChainCompletes() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "should-be-removed");
        request.addHeader("X-Tenant-Id", "tenant-x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> { /* no-op */ };

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull();
        assertThat(MDC.get(MdcKeys.TENANT_ID)).isNull();
    }
}
