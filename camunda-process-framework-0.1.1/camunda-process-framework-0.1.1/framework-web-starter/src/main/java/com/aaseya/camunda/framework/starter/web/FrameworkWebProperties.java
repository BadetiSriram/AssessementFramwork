package com.aaseya.camunda.framework.starter.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the framework web auto-configuration.
 *
 * <p>All properties are bound under the {@code framework.web} prefix.
 *
 * <h2>Available properties</h2>
 * <ul>
 *   <li>{@code framework.web.exception-handler-enabled} — when {@code false}, the
 *       auto-configured {@link GlobalExceptionHandler} bean is not registered, allowing
 *       a consuming service to supply its own exception-handling strategy without
 *       fighting Spring's condition ordering.  Defaults to {@code true}.</li>
 * </ul>
 *
 * <h2>Example: disable the framework handler</h2>
 * <pre>{@code
 * # application.yml
 * framework:
 *   web:
 *     exception-handler-enabled: false
 * }</pre>
 */
@ConfigurationProperties(prefix = "framework.web")
public class FrameworkWebProperties {

    /**
     * Whether the framework's global exception handler is registered.
     *
     * <p>Set to {@code false} if the consuming service provides its own
     * {@code @RestControllerAdvice} and does not want the framework default.
     * Defaults to {@code true}.
     */
    private boolean exceptionHandlerEnabled = true;

    /** Public no-arg constructor required by Spring Boot's properties binding. */
    public FrameworkWebProperties() {
    }

    /**
     * Returns whether the global exception handler is enabled.
     *
     * @return {@code true} if the framework handler should be registered (the default)
     */
    public boolean isExceptionHandlerEnabled() {
        return exceptionHandlerEnabled;
    }

    /**
     * Sets whether the global exception handler is enabled.
     *
     * @param exceptionHandlerEnabled {@code false} to suppress the framework's default handler
     */
    public void setExceptionHandlerEnabled(boolean exceptionHandlerEnabled) {
        this.exceptionHandlerEnabled = exceptionHandlerEnabled;
    }
}
