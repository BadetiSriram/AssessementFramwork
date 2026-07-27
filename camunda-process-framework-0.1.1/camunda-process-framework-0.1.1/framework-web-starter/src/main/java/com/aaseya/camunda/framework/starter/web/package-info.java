/**
 * Spring Boot auto-configuration starter for the framework REST API layer. This package
 * provides three capabilities: (1) a typed {@link com.aaseya.camunda.framework.starter.web.Response}
 * record that wraps every successful API response in a consistent envelope carrying the
 * business payload alongside a {@code correlationId} (sourced from the MDC) and an
 * ISO-8601 {@code timestamp}; (2) Jakarta Bean Validation integration via
 * {@code spring-boot-starter-validation} so that {@code @Valid @RequestBody} parameters and
 * method-level {@code @Validated} constraints are automatically enforced; and (3) a global
 * exception handler ({@link com.aaseya.camunda.framework.starter.web.GlobalExceptionHandler})
 * that maps the framework exception hierarchy — {@code BusinessException},
 * {@code RetryableException}, {@code NonRetryableException}, and {@code TechnicalException}
 * — together with Jakarta Validation failures, to RFC 7807
 * {@link org.springframework.http.ProblemDetail} responses, so consuming services expose
 * machine-readable, consistently structured error payloads without boilerplate. All tuneable
 * parameters are exposed under the {@code framework.web.*} property namespace via
 * {@link com.aaseya.camunda.framework.starter.web.FrameworkWebProperties}.
 */
package com.aaseya.camunda.framework.starter.web;
