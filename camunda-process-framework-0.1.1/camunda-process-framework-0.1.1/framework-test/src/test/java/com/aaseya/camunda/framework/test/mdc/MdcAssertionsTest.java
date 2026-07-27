package com.aaseya.camunda.framework.test.mdc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Self-tests for {@link MdcAssertions}.
 *
 * <p>Verifies that each assertion helper behaves correctly when the MDC
 * is in the expected state (passes without throwing) and when it is in a
 * violating state (throws {@link AssertionError}).</p>
 */
class MdcAssertionsTest {

    private static final String TEST_KEY = "testKey";
    private static final String TEST_VALUE = "testValue";

    @AfterEach
    void cleanup() {
        // Ensure MDC is always clean between tests
        MdcAssertions.clearMdc();
    }

    // -------------------------------------------------------------------------
    // assertMdcContains
    // -------------------------------------------------------------------------

    @Test
    void assertMdcContains_passesWhenKeyHasExpectedValue() {
        MDC.put(TEST_KEY, TEST_VALUE);

        assertThatCode(() -> MdcAssertions.assertMdcContains(TEST_KEY, TEST_VALUE))
                .doesNotThrowAnyException();
    }

    @Test
    void assertMdcContains_failsWhenKeyIsAbsent() {
        // MDC is empty here

        assertThatThrownBy(() -> MdcAssertions.assertMdcContains(TEST_KEY, TEST_VALUE))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertMdcContains_failsWhenKeyHasDifferentValue() {
        MDC.put(TEST_KEY, "wrongValue");

        assertThatThrownBy(() -> MdcAssertions.assertMdcContains(TEST_KEY, TEST_VALUE))
                .isInstanceOf(AssertionError.class);
    }

    // -------------------------------------------------------------------------
    // assertMdcAbsent
    // -------------------------------------------------------------------------

    @Test
    void assertMdcAbsent_passesWhenKeyIsNotInMdc() {
        // MDC is empty — key is absent

        assertThatCode(() -> MdcAssertions.assertMdcAbsent(TEST_KEY))
                .doesNotThrowAnyException();
    }

    @Test
    void assertMdcAbsent_failsWhenKeyIsPresent() {
        MDC.put(TEST_KEY, TEST_VALUE);

        assertThatThrownBy(() -> MdcAssertions.assertMdcAbsent(TEST_KEY))
                .isInstanceOf(AssertionError.class);
    }

    // -------------------------------------------------------------------------
    // assertMdcEmpty
    // -------------------------------------------------------------------------

    @Test
    void assertMdcEmpty_passesWhenMdcHasNoEntries() {
        // MDC is empty after @AfterEach cleanup runs between tests

        assertThatCode(MdcAssertions::assertMdcEmpty)
                .doesNotThrowAnyException();
    }

    @Test
    void assertMdcEmpty_failsWhenMdcHasAnyEntry() {
        MDC.put(TEST_KEY, TEST_VALUE);

        assertThatThrownBy(MdcAssertions::assertMdcEmpty)
                .isInstanceOf(AssertionError.class);
    }

    // -------------------------------------------------------------------------
    // clearMdc
    // -------------------------------------------------------------------------

    @Test
    void clearMdc_removesAllEntries() {
        MDC.put(TEST_KEY, TEST_VALUE);
        MDC.put("anotherKey", "anotherValue");

        MdcAssertions.clearMdc();

        assertThatCode(MdcAssertions::assertMdcEmpty)
                .doesNotThrowAnyException();
    }

    @Test
    void clearMdc_isIdempotentOnEmptyMdc() {
        // Calling clear on an already-empty MDC must not throw
        assertThatCode(MdcAssertions::clearMdc)
                .doesNotThrowAnyException();
    }
}
