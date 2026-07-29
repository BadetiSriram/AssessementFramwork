package com.aaseya.incident.web;

import com.aaseya.camunda.framework.core.mdc.MdcKeys;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

/**
 * Fills two gaps the framework's GlobalExceptionHandler leaves open.
 *
 * <p>It handles bean validation and the framework exception types, but a body Jackson can't parse
 * and a path variable that won't convert both fall through to its {@code Exception} catch-all and
 * come back as 500 "An unexpected error occurred." That's misleading: a malformed body, a field of
 * the wrong type and an id that isn't a UUID are all the caller's mistake, and a 500 tells them to
 * raise a ticket instead of fixing their request.
 *
 * <p>Ordered ahead of the framework advice so these two types are claimed here; everything else
 * still falls through to it unchanged.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class IncidentWebExceptionHandler {

    /** Body isn't valid JSON, or a field is the wrong type (e.g. "recordCount": "lots"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return badRequest("Malformed request body", "Request body could not be parsed",
                rootMessage(ex));
    }

    /** Path or query parameter that won't convert - in practice an id that isn't a UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String required = ex.getRequiredType() == null ? "the expected type"
                : ex.getRequiredType().getSimpleName();
        return badRequest("Invalid request parameter",
                "Parameter '" + ex.getName() + "' must be a valid " + required,
                String.valueOf(ex.getValue()));
    }

    private static ResponseEntity<ProblemDetail> badRequest(String title, String detail,
                                                            String rejectedValue) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle(title);
        problem.setProperty("rejectedValue", rejectedValue);
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        problem.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Jackson's message names the offending field and value, which is the useful part. The
     * framework handler deliberately hides exception messages to avoid leaking internals; here the
     * cause is a parse error on input the caller sent us, so there is nothing of ours to leak.
     */
    private static String rootMessage(Throwable ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        String message = cause.getMessage();
        if (message == null) {
            return "unparseable";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
