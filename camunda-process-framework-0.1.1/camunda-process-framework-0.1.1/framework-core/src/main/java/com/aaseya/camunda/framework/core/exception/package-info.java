/**
 * Framework exception hierarchy for the Camunda process framework.
 *
 * <p>All framework-managed throwables extend {@link com.aaseya.camunda.framework.core.exception.FrameworkException}.
 * The two primary branches are:
 * <ul>
 *   <li>{@link com.aaseya.camunda.framework.core.exception.BusinessException} — expected
 *       business-flow failures routed to BPMN error boundary events (HTTP 422).</li>
 *   <li>{@link com.aaseya.camunda.framework.core.exception.TechnicalException} — infrastructure
 *       or integration failures, further sub-typed into
 *       {@link com.aaseya.camunda.framework.core.exception.RetryableException} (HTTP 503)
 *       and {@link com.aaseya.camunda.framework.core.exception.NonRetryableException} (HTTP 500).</li>
 * </ul>
 *
 * <p>Pre-existing types {@link com.aaseya.camunda.framework.core.exception.IllegalStateTransitionException}
 * and {@link com.aaseya.camunda.framework.core.exception.BusinessError} remain in this package
 * for backward compatibility and serve the state-machine and {@code WorkResult} contracts
 * respectively.
 */
package com.aaseya.camunda.framework.core.exception;
