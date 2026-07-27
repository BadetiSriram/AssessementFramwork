package com.aaseya.camunda.framework.starter.observability;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Spring Boot auto-configuration for the framework observability starter.
 *
 * <p>This class wires the following infrastructure automatically when the
 * starter is on the classpath:
 * <ul>
 *   <li>{@link MdcCorrelationFilter} — propagates correlation and tenant
 *       identifiers into the SLF4J MDC for every HTTP request (requires
 *       {@code jakarta.servlet.Filter} on the classpath and property
 *       {@code framework.observability.mdc.enabled=true}, which is the default).</li>
 * </ul>
 *
 * <p>This class does <strong>not</strong> register a {@code MeterRegistry} bean
 * (Spring Boot Actuator provides that automatically) and does not register an
 * OTel tracer bean ({@code micrometer-tracing-bridge-otel} provides its own
 * auto-configuration).
 *
 * <p>This class does <strong>not</strong> register a {@link FrameworkCounters}
 * bean because the domain name is application-specific. Services should declare
 * their own bean:
 *
 * <pre>{@code
 * @Bean
 * public FrameworkCounters orderCounters(MeterRegistry registry,
 *                                        FrameworkObservabilityProperties props) {
 *     String prefix = props.getMetrics().getBusinessCounterPrefix();
 *     return new FrameworkCounters(registry, prefix.isBlank() ? "orders" : prefix);
 * }
 * }</pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(FrameworkObservabilityProperties.class)
public class FrameworkObservabilityAutoConfiguration {

    /**
     * Registers {@link MdcCorrelationFilter} at a high precedence so MDC context
     * is populated before any other filter that may write structured log lines.
     *
     * <p>The registration is conditional on:
     * <ul>
     *   <li>{@code jakarta.servlet.Filter} being present on the classpath (i.e. a
     *       servlet-based web application).</li>
     *   <li>Property {@code framework.observability.mdc.enabled} being {@code true}
     *       (or absent — defaults to {@code true}).</li>
     * </ul>
     *
     * @param props resolved observability properties
     * @return filter registration at {@link Ordered#HIGHEST_PRECEDENCE}{@code + 100}
     */
    @Bean
    @ConditionalOnClass(Filter.class)
    @ConditionalOnProperty(
            prefix = "framework.observability.mdc",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<MdcCorrelationFilter> mdcCorrelationFilterRegistration(
            FrameworkObservabilityProperties props) {

        FilterRegistrationBean<MdcCorrelationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MdcCorrelationFilter(props.getMdc()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        registration.addUrlPatterns("/*");
        registration.setName("mdcCorrelationFilter");
        return registration;
    }
}
