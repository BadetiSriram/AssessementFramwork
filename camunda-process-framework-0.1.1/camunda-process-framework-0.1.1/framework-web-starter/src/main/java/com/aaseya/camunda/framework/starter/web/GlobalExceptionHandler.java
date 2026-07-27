package com.aaseya.camunda.framework.starter.web;

import com.aaseya.camunda.framework.core.exception.BusinessException;
import com.aaseya.camunda.framework.core.exception.NonRetryableException;
import com.aaseya.camunda.framework.core.exception.RetryableException;
import com.aaseya.camunda.framework.core.exception.TechnicalException;
import com.aaseya.camunda.framework.core.mdc.MdcKeys;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler that maps framework and validation exceptions to
 * RFC 7807 {@link ProblemDetail} responses.
 *
 * <p>Registered automatically by {@link FrameworkWebAutoConfiguration} when:
 * <ul>
 *   <li>{@code org.springframework.web.bind.annotation.RestControllerAdvice} is on the
 *       classpath (i.e., Spring Web MVC is present), and</li>
 *   <li>{@code framework.web.exception-handler-enabled} is {@code true} (the default).</li>
 * </ul>
 *
 * <p>A consuming service may disable this handler via
 * {@code framework.web.exception-handler-enabled=false} and supply its own
 * {@code @RestControllerAdvice}.  Alternatively, the {@link FrameworkWebAutoConfiguration}
 * backs off on any existing {@code GlobalExceptionHandler} bean, so extending or
 * replacing this class works without disabling the auto-configuration entirely.
 *
 * <h2>Handler priority</h2>
 * <p>Spring MVC evaluates {@code @ExceptionHandler} methods from most-specific to
 * least-specific exception type.  Within this class the ordering is:
 * <ol>
 *   <li>Validation errors ({@code MethodArgumentNotValidException},
 *       {@code ConstraintViolationException}) → 400</li>
 *   <li>{@link BusinessException} → 422</li>
 *   <li>{@link RetryableException} → 503  (before the abstract TechnicalException catch-all)</li>
 *   <li>{@link NonRetryableException} → 500 (before the abstract TechnicalException catch-all)</li>
 *   <li>{@link TechnicalException} catch-all → 500</li>
 *   <li>{@link Exception} catch-all → 500 (no internal detail leaked)</li>
 * </ol>
 *
 * <h2>Common properties on every ProblemDetail</h2>
 * <ul>
 *   <li>{@code correlationId} — value of {@link MdcKeys#CORRELATION_ID} at the time the
 *       handler fires; {@code null} when the observability filter is not active.</li>
 *   <li>{@code timestamp} — ISO-8601 string of {@link Instant#now()} at handler invocation.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // Validation — 400 Bad Request
    // -------------------------------------------------------------------------

    /**
     * Handles Spring MVC bean-validation failures on {@code @Valid @RequestBody} parameters.
     *
     * <p>Each field error is mapped to a {@code {field, message, rejectedValue}} entry in the
     * {@code fieldErrors} property of the {@link ProblemDetail}.
     *
     * @param ex the validation exception raised by Spring's argument resolver
     * @return 400 response with field-level error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        log.debug("Bean validation failed: {} field error(s)", ex.getBindingResult().getErrorCount());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation error");

        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldErrorMap)
                .collect(Collectors.toList());

        problem.setProperty("fieldErrors", fieldErrors);
        addCommonProperties(problem);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handles Jakarta Bean Validation constraint violations raised outside of Spring MVC's
     * argument binding (e.g., from method-level {@code @Validated} on service beans, or
     * thrown explicitly from application code).
     *
     * <p>Each violation is mapped to a {@code {propertyPath, message, invalidValue}} entry
     * in the {@code violations} property of the {@link ProblemDetail}.
     *
     * @param ex the constraint violation exception
     * @return 400 response with constraint violation details
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex) {

        log.debug("Constraint violation: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Constraint validation failed");
        problem.setTitle("Validation error");

        List<Map<String, String>> violations = ex.getConstraintViolations().stream()
                .map(cv -> Map.of(
                        "propertyPath", cv.getPropertyPath().toString(),
                        "message", cv.getMessage(),
                        "invalidValue", cv.getInvalidValue() == null
                                ? "null"
                                : cv.getInvalidValue().toString()))
                .collect(Collectors.toList());

        problem.setProperty("violations", violations);
        addCommonProperties(problem);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    // -------------------------------------------------------------------------
    // Business — 422 Unprocessable Entity
    // -------------------------------------------------------------------------

    /**
     * Handles {@link BusinessException} — a domain-level rule violation that the caller
     * can recover from by correcting the request (e.g., invalid state transition).
     *
     * @param ex the business exception
     * @return 422 response with the stable error code and human-readable detail
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex) {

        log.warn("Business rule violation [{}]: {}", ex.errorCode(), ex.errorMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.errorMessage());
        problem.setTitle("Business rule violation");
        problem.setProperty("errorCode", ex.errorCode());
        addCommonProperties(problem);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    // -------------------------------------------------------------------------
    // Technical — 503 / 500
    // -------------------------------------------------------------------------

    /**
     * Handles {@link RetryableException} — a transient infrastructure failure.
     *
     * <p>The response includes a {@code Retry-After: 5} header (seconds) to hint to
     * HTTP clients that retrying after a brief delay is appropriate.
     *
     * @param ex the retryable exception
     * @return 503 response with {@code Retry-After} header
     */
    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<ProblemDetail> handleRetryableException(RetryableException ex) {

        log.error("Retryable technical failure [{}]: {}", ex.errorCode(), ex.errorMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, ex.errorMessage());
        problem.setTitle("Service temporarily unavailable");
        problem.setProperty("errorCode", ex.errorCode());
        addCommonProperties(problem);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5")
                .body(problem);
    }

    /**
     * Handles {@link NonRetryableException} — a permanent infrastructure failure that
     * retrying would not resolve (e.g., misconfiguration, schema mismatch).
     *
     * @param ex the non-retryable exception
     * @return 500 response
     */
    @ExceptionHandler(NonRetryableException.class)
    public ResponseEntity<ProblemDetail> handleNonRetryableException(NonRetryableException ex) {

        log.error("Non-retryable technical failure [{}]: {}", ex.errorCode(), ex.errorMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, ex.errorMessage());
        problem.setTitle("Internal error");
        problem.setProperty("errorCode", ex.errorCode());
        addCommonProperties(problem);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    /**
     * Catch-all for {@link TechnicalException} subclasses not handled by the more-specific
     * handlers above.  Downstream code may introduce additional concrete subtypes of the
     * abstract {@code TechnicalException}; this handler ensures they still produce a
     * well-formed 500 response rather than falling through to the generic handler.
     *
     * @param ex the unrecognised technical exception
     * @return 500 response
     */
    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<ProblemDetail> handleTechnicalException(TechnicalException ex) {

        log.error("Technical failure [{}]: {}", ex.errorCode(), ex.errorMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, ex.errorMessage());
        problem.setTitle("Internal error");
        problem.setProperty("errorCode", ex.errorCode());
        addCommonProperties(problem);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    /**
     * Last-resort catch-all for any {@link Exception} not matched by a more-specific handler.
     *
     * <p><strong>Security note:</strong> {@code ex.getMessage()} is intentionally NOT
     * included in the response body to prevent leaking internal stack-trace fragments or
     * sensitive class/field names to external callers.  The exception is logged at ERROR
     * level with full detail for operational visibility.
     *
     * @param ex the unhandled exception
     * @return 500 response with a generic, non-leaking detail message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {

        log.error("Unexpected error", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Unexpected error");
        addCommonProperties(problem);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Adds the {@code correlationId} and {@code timestamp} properties that are present on
     * every problem detail response produced by this handler.
     *
     * @param problem the problem detail to enrich; mutated in place
     */
    private static void addCommonProperties(ProblemDetail problem) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        problem.setProperty("timestamp", Instant.now().toString());
    }

    /**
     * Maps a Spring {@link FieldError} to a plain {@link Map} suitable for JSON serialisation.
     *
     * @param fe the field error from the binding result
     * @return map with keys {@code field}, {@code message}, and {@code rejectedValue}
     */
    private static Map<String, String> toFieldErrorMap(FieldError fe) {
        return Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() == null ? "" : fe.getDefaultMessage(),
                "rejectedValue", fe.getRejectedValue() == null
                        ? "null"
                        : fe.getRejectedValue().toString());
    }
}
