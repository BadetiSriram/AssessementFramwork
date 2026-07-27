package com.aaseya.camunda.framework.core.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the framework exception hierarchy: correct inheritance, field propagation,
 * cause chaining, and constructor validation guards.
 */
class FrameworkExceptionTest {

    // ---- BusinessException — basic field propagation ----

    @Test
    void businessException_carriesCodeAndMessage() {
        BusinessException ex = new BusinessException("BOOKING_INVALID", "Booking not found");

        assertThat(ex.errorCode()).isEqualTo("BOOKING_INVALID");
        assertThat(ex.errorMessage()).isEqualTo("Booking not found");
        assertThat(ex.getMessage()).isEqualTo("Booking not found");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void businessException_withCause_preservesCause() {
        RuntimeException cause = new RuntimeException("root cause");
        BusinessException ex = new BusinessException("RULE_VIOLATED", "Business rule violated", cause);

        assertThat(ex.errorCode()).isEqualTo("RULE_VIOLATED");
        assertThat(ex.errorMessage()).isEqualTo("Business rule violated");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    // ---- Inheritance checks ----

    @Test
    void retryableException_isTechnicalException() {
        assertThat(new RetryableException("TIMEOUT", "Connection timed out"))
                .isInstanceOf(TechnicalException.class)
                .isInstanceOf(FrameworkException.class)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void nonRetryableException_isTechnicalException() {
        assertThat(new NonRetryableException("SCHEMA_MISMATCH", "Column missing"))
                .isInstanceOf(TechnicalException.class)
                .isInstanceOf(FrameworkException.class)
                .isInstanceOf(RuntimeException.class);
    }

    // ---- Constructor validation: errorCode ----

    @Test
    void frameworkException_rejectsNullErrorCode() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new BusinessException(null, "some message"));
    }

    @Test
    void frameworkException_rejectsBlankErrorCode() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new BusinessException("   ", "some message"));
    }

    // ---- Constructor validation: errorMessage ----

    @Test
    void frameworkException_rejectsNullErrorMessage() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new BusinessException("SOME_CODE", null));
    }

    @Test
    void frameworkException_rejectsBlankErrorMessage() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new BusinessException("SOME_CODE", ""));
    }

    // ---- RetryableException and NonRetryableException — cause chaining ----

    @Test
    void retryableException_withCause_preservesCause() {
        IOException cause = new IOException("connection reset");
        RetryableException ex = new RetryableException("DB_CONTENTION", "Lock timeout", cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo("DB_CONTENTION");
    }

    @Test
    void nonRetryableException_withCause_preservesCause() {
        IOException cause = new IOException("corrupt data");
        NonRetryableException ex = new NonRetryableException("CORRUPT_PAYLOAD", "Cannot deserialise", cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo("CORRUPT_PAYLOAD");
    }

    // ---- Helper — keeps the test self-contained without importing java.io.IOException ----

    private static final class IOException extends RuntimeException {
        IOException(String message) {
            super(message);
        }
    }
}
