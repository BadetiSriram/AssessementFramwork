package com.aaseya.camunda.framework.starter.web;

import com.aaseya.camunda.framework.core.mdc.MdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * Unit tests for {@link Response}.
 */
class ResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.remove(MdcKeys.CORRELATION_ID);
    }

    @Test
    @DisplayName("ok wraps data as the record's data component")
    void ok_wrapsData() {
        Response<String> response = Response.ok("hello");

        assertThat(response.data()).isEqualTo("hello");
    }

    @Test
    @DisplayName("ok populates meta with a timestamp within 5 seconds of now")
    void ok_populatesMetaWithCurrentTimestamp() {
        Instant before = Instant.now();

        Response<String> response = Response.ok("data");

        assertThat(response.meta()).isNotNull();
        assertThat(response.meta().timestamp())
                .isAfterOrEqualTo(before)
                .isCloseTo(Instant.now(), within(5, SECONDS));
    }

    @Test
    @DisplayName("ok reads correlation ID from MDC when present")
    void ok_readsCorrelationIdFromMdc() {
        MDC.put(MdcKeys.CORRELATION_ID, "abc-123");

        Response<String> response = Response.ok("x");

        assertThat(response.meta().correlationId()).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("ok with explicit correlation ID sets that field on meta")
    void ok_withExplicitCorrelationId() {
        Response<String> response = Response.ok("payload", "explicit-id-999");

        assertThat(response.meta().correlationId()).isEqualTo("explicit-id-999");
    }
}
