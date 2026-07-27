package com.aaseya.camunda.framework.starter.web;

import com.aaseya.camunda.framework.core.mdc.MdcKeys;
import org.slf4j.MDC;

import java.time.Instant;

/**
 * Generic response envelope for all framework REST endpoints.
 *
 * <p>Every successful API response is wrapped in this record so consumers receive a
 * consistent structure regardless of which resource they call:
 *
 * <pre>{@code
 * {
 *   "data": { ... },
 *   "meta": {
 *     "correlationId": "abc-123",
 *     "timestamp": "2026-07-21T10:15:30.123456Z"
 *   }
 * }
 * }</pre>
 *
 * <h2>Usage — controller method</h2>
 * <pre>{@code
 * @GetMapping("/orders/{id}")
 * public Response<OrderDto> getOrder(@PathVariable String id) {
 *     OrderDto dto = orderService.find(id);
 *     return Response.ok(dto);
 * }
 * }</pre>
 *
 * <p>The {@code correlationId} in the {@link Meta} envelope is read automatically from the
 * MDC key {@link MdcKeys#CORRELATION_ID}. The observability starter's
 * {@code MdcCorrelationFilter} populates that key from the {@code X-Correlation-Id} request
 * header, so no manual wiring is required in controllers.
 *
 * @param <T>  the payload type
 * @param data the business payload; may be {@code null} for empty-body responses
 * @param meta correlation and timestamp metadata; never {@code null}
 */
public record Response<T>(T data, Meta meta) {

    /**
     * Metadata carried alongside every successful response.
     *
     * @param correlationId the request-scoped correlation identifier; may be {@code null}
     *                      if the observability filter is not active (e.g., in unit tests)
     * @param timestamp     the instant at which the response was assembled; never {@code null}
     */
    public record Meta(String correlationId, Instant timestamp) {

        /**
         * Creates a {@link Meta} snapshot stamped with the current instant.
         *
         * @param correlationId the correlation identifier to embed; {@code null} is accepted
         *                      when no correlation context is available
         * @return a new {@code Meta} with {@link Instant#now()} as the timestamp
         */
        public static Meta now(String correlationId) {
            return new Meta(correlationId, Instant.now());
        }
    }

    /**
     * Wraps {@code data} in a {@code Response}, reading the correlation identifier
     * automatically from the MDC key {@link MdcKeys#CORRELATION_ID}.
     *
     * <p>This is the preferred factory for controller methods: the MDC value is populated
     * upstream by the observability filter before the controller is invoked.
     *
     * @param <T>  the payload type
     * @param data the business payload
     * @return a new {@code Response} with MDC-sourced correlation ID and current timestamp
     */
    public static <T> Response<T> ok(T data) {
        return new Response<>(data, Meta.now(MDC.get(MdcKeys.CORRELATION_ID)));
    }

    /**
     * Wraps {@code data} in a {@code Response} with an explicitly supplied correlation
     * identifier, bypassing MDC lookup.
     *
     * <p>Use this overload when the correlation ID is known from a non-HTTP source (e.g.,
     * a message broker header) and has not been placed in the MDC.
     *
     * @param <T>           the payload type
     * @param data          the business payload
     * @param correlationId the correlation identifier to embed; {@code null} is accepted
     * @return a new {@code Response} with the supplied correlation ID and current timestamp
     */
    public static <T> Response<T> ok(T data, String correlationId) {
        return new Response<>(data, Meta.now(correlationId));
    }
}
