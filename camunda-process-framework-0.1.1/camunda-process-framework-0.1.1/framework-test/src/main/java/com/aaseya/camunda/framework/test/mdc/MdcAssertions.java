package com.aaseya.camunda.framework.test.mdc;

import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssertJ-style helpers for verifying SLF4J MDC (Mapped Diagnostic Context) state
 * in JUnit tests.
 *
 * <h2>Purpose</h2>
 * <p>MDC leakage between tests is a common source of flaky log-context bugs.  If an
 * earlier test sets a correlation ID or tenant identifier and fails to clear it, later
 * tests may emit log lines with stale context or — worse — use that context to make
 * routing decisions.</p>
 *
 * <h2>Recommended usage</h2>
 * <pre>{@code
 * class MyServiceTest {
 *
 *     @AfterEach
 *     void clearMdc() {
 *         MdcAssertions.clearMdc();
 *     }
 *
 *     @Test
 *     void worker_putsTenantIdIntoMdc() {
 *         // ... call code under test ...
 *         MdcAssertions.assertMdcContains("tenantId", "acme");
 *     }
 *
 *     @Test
 *     void handler_doesNotLeakCorrelationId() {
 *         // ... call code under test ...
 *         MdcAssertions.assertMdcAbsent("correlationId");
 *     }
 * }
 * }</pre>
 *
 * <p>Prefer wiring {@link #clearMdc()} into your test's {@code @AfterEach} to catch
 * leaked MDC context (a common source of flaky-log-context bugs).</p>
 */
public final class MdcAssertions {

    /**
     * Prevents instantiation — this class is a static assertion helper only.
     */
    private MdcAssertions() {
        throw new AssertionError("MdcAssertions must not be instantiated");
    }

    /**
     * Asserts that the MDC contains the given key with the given expected value.
     *
     * @param key           the MDC key to look up
     * @param expectedValue the value that must be present for that key
     * @throws AssertionError if the key is absent or has a different value
     */
    public static void assertMdcContains(String key, String expectedValue) {
        String actual = MDC.get(key);
        assertThat(actual)
                .as("MDC key '%s' should be '%s' but was '%s'", key, expectedValue, actual)
                .isEqualTo(expectedValue);
    }

    /**
     * Asserts that the MDC does not contain the given key (or that it is mapped to
     * {@code null}).
     *
     * @param key the MDC key that must be absent
     * @throws AssertionError if the key is present with a non-null value
     */
    public static void assertMdcAbsent(String key) {
        String actual = MDC.get(key);
        assertThat(actual)
                .as("MDC key '%s' should be absent but was '%s'", key, actual)
                .isNull();
    }

    /**
     * Asserts that the MDC context map is completely empty (no keys at all).
     *
     * <p>Use this in {@code @AfterEach} hooks to verify that the code under test has
     * properly cleaned up every key it placed into the MDC.</p>
     *
     * @throws AssertionError if the MDC context map is non-null and non-empty
     */
    public static void assertMdcEmpty() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        boolean isEmpty = (contextMap == null || contextMap.isEmpty());
        assertThat(isEmpty)
                .as("MDC context map should be empty but contained: %s",
                        contextMap != null ? contextMap : "<null>")
                .isTrue();
    }

    /**
     * Clears all entries from the MDC context map.
     *
     * <p>Safe to call even when the MDC is already empty.  Intended for use in
     * {@code @AfterEach} test lifecycle methods to prevent context leaking between
     * test cases.</p>
     */
    public static void clearMdc() {
        MDC.clear();
    }
}
