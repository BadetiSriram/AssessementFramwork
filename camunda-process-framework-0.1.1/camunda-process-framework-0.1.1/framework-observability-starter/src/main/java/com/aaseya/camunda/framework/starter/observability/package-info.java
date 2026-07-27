/**
 * Spring Boot auto-configuration starter for structured observability in the Camunda process
 * framework. This package provides three capabilities: (1) an MDC servlet filter that injects
 * a correlation identifier and tenant identifier into every HTTP request so that structured log
 * lines are automatically correlated across service boundaries; (2) a {@code FrameworkCounters}
 * helper that wraps Micrometer {@code Counter} registration behind a domain-aware API so that
 * business state transitions emit consistently named Prometheus metrics; and (3) wiring of
 * Micrometer's OpenTelemetry tracing bridge and Prometheus registry through standard Spring Boot
 * auto-configuration, requiring no manual instrumentation in consuming services.
 */
package com.aaseya.camunda.framework.starter.observability;
