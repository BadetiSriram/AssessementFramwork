package com.aaseya.camunda.framework.starter.observability;

import com.aaseya.camunda.framework.core.mdc.MdcKeys;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that propagates a correlation identifier and tenant identifier
 * into the SLF4J MDC for the duration of each HTTP request.
 *
 * <p>The correlation id is read from the configurable request header
 * (default {@code X-Correlation-Id}). When the header is absent and
 * {@link FrameworkObservabilityProperties.Mdc#isGenerateIfAbsent()} is
 * {@code true} a random UUID is generated. The resolved value is echoed back
 * to the caller in the HTTP response so it can be used for end-to-end tracing.
 *
 * <p>The tenant identifier is read from the configurable header
 * (default {@code X-Tenant-Id}) when present and stored under
 * {@link MdcKeys#TENANT_ID}.
 *
 * <p>Both MDC keys are unconditionally removed in the {@code finally} block so
 * they cannot leak across requests on pooled threads.
 */
public class MdcCorrelationFilter implements Filter {

    private final FrameworkObservabilityProperties.Mdc config;

    /**
     * Creates a new filter with the supplied MDC configuration.
     *
     * @param config non-null MDC configuration
     */
    public MdcCorrelationFilter(FrameworkObservabilityProperties.Mdc config) {
        if (config == null) {
            throw new IllegalArgumentException("MDC config must not be null");
        }
        this.config = config;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = httpRequest.getHeader(config.getHeaderName());
        if (correlationId == null || correlationId.isBlank()) {
            if (config.isGenerateIfAbsent()) {
                correlationId = UUID.randomUUID().toString();
            }
        }

        String tenantId = httpRequest.getHeader(config.getTenantIdHeaderName());

        try {
            if (correlationId != null) {
                MDC.put(MdcKeys.CORRELATION_ID, correlationId);
                httpResponse.setHeader(config.getHeaderName(), correlationId);
            }
            if (tenantId != null && !tenantId.isBlank()) {
                MDC.put(MdcKeys.TENANT_ID, tenantId);
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID);
            MDC.remove(MdcKeys.TENANT_ID);
        }
    }
}
