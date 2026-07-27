package com.aaseya.camunda.framework.core.worker;

import com.aaseya.camunda.framework.core.exception.BusinessException;
import com.aaseya.camunda.framework.core.exception.NonRetryableException;
import com.aaseya.camunda.framework.core.exception.RetryableException;
import com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard;
import com.aaseya.camunda.framework.core.mdc.MdcKeys;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the {@link BaseWorker} template-method execution skeleton: happy path,
 * business error routing, technical failure propagation, idempotency short-circuit,
 * MDC enrichment, hooks (validate / mapResponse / handleException), and
 * framework-exception auto-translation.
 * Uses {@code RETURNS_DEEP_STUBS} on {@link JobClient} to avoid brittle command-step
 * interface chaining.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BaseWorkerTest {

    /** Deep-stub job client so builder chains resolve without explicit step stubs. */
    private JobClient jobClient;

    @Mock
    private ActivatedJob activatedJob;

    @Mock
    private IdempotencyGuard guard;

    private SimpleMeterRegistry meterRegistry;
    private VariableMapper mapper;

    /** Minimal concrete worker returning a configurable result. */
    private static class TestWorker extends BaseWorker<TestVars> {
        WorkResult resultToReturn;
        RuntimeException exceptionToThrow;

        TestWorker(VariableMapper mapper, IdempotencyGuard guard,
                   io.micrometer.core.instrument.MeterRegistry meterRegistry) {
            super(mapper, guard, meterRegistry);
        }

        @Override
        protected Class<TestVars> varsType() {
            return TestVars.class;
        }

        @Override
        protected WorkResult doWork(TestVars vars, ActivatedJob job) {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return resultToReturn;
        }
    }

    record TestVars(String businessKey) {}

    private TestWorker worker;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        mapper = VariableMapper.createDefault();
        worker = new TestWorker(mapper, guard, meterRegistry);

        // Deep-stub so any builder chain returns a mock that returns a mock
        jobClient = mock(JobClient.class, RETURNS_DEEP_STUBS);

        // Common job stubs
        when(activatedJob.getKey()).thenReturn(123L);
        when(activatedJob.getType()).thenReturn("test-worker");
        when(activatedJob.getProcessInstanceKey()).thenReturn(456L);
        when(activatedJob.getElementId()).thenReturn("Task_Test");
        when(activatedJob.getTenantId()).thenReturn(null);

        // getVariablesAsType returns a populated record for deserialization
        when(activatedJob.getVariablesAsType(any())).thenReturn(new TestVars("BK-001"));
        // getVariables provides the JSON string used by extractBusinessKey
        when(activatedJob.getVariables()).thenReturn("{\"businessKey\":\"BK-001\"}");
    }

    // ---- original 5 tests (must remain green) ----

    @Test
    void happyPath_completedResult_callsCompleteCommand() {
        worker.resultToReturn = WorkResult.completed(Map.of("output", "ok"));
        when(guard.check(any(), any())).thenReturn(false);

        worker.execute(jobClient, activatedJob);

        verify(jobClient).newCompleteCommand(activatedJob);
        verify(guard).record("BK-001", "Task_Test", null);

        double count = meterRegistry.counter("framework_job_completed_total", "type", "test-worker").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void businessError_callsThrowErrorCommand() {
        worker.resultToReturn = WorkResult.businessError("BOOKING_INVALID", "Booking not found");
        when(guard.check(any(), any())).thenReturn(false);

        worker.execute(jobClient, activatedJob);

        verify(jobClient).newThrowErrorCommand(activatedJob);

        double count = meterRegistry.counter("framework_job_business_error_total",
                "type", "test-worker", "code", "BOOKING_INVALID").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void runtimeException_incrementsFailedMetricAndRethrows() {
        worker.exceptionToThrow = new RuntimeException("transient failure");
        when(guard.check(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> worker.execute(jobClient, activatedJob))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("transient failure");

        double count = meterRegistry.counter("framework_job_failed_total", "type", "test-worker").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void idempotencyGuardTrue_shortCircuitsToComplete() {
        when(guard.check("BK-001", "Task_Test")).thenReturn(true);

        worker.execute(jobClient, activatedJob);

        verify(jobClient).newCompleteCommand(activatedJob);
        // doWork must NOT have been called — resultToReturn stays null with no NPE
        verify(guard, never()).record(any(), any(), any());

        double count = meterRegistry.counter("framework_job_replayed_total", "type", "test-worker").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void compensatedResult_callsCompleteWithNoVariables() {
        worker.resultToReturn = WorkResult.compensated();
        when(guard.check(any(), any())).thenReturn(false);

        worker.execute(jobClient, activatedJob);

        verify(jobClient).newCompleteCommand(activatedJob);
    }

    // ---- 8 new tests (Slice 5.2 + 5.4) ----

    /**
     * Named inner class used by {@code execute_pushesJobKeyAndWorkerNameIntoMdc} so that
     * {@code getSimpleName()} returns a non-blank value (anonymous classes return {@code ""}).
     */
    private static class MdcProbingWorker extends BaseWorker<TestVars> {
        final AtomicReference<String> capturedJobKey = new AtomicReference<>();
        final AtomicReference<String> capturedWorkerName = new AtomicReference<>();

        MdcProbingWorker(VariableMapper mapper, IdempotencyGuard guard,
                         io.micrometer.core.instrument.MeterRegistry meterRegistry) {
            super(mapper, guard, meterRegistry);
        }

        @Override
        protected Class<TestVars> varsType() {
            return TestVars.class;
        }

        @Override
        protected WorkResult doWork(TestVars vars, ActivatedJob job) {
            capturedJobKey.set(MDC.get(MdcKeys.JOB_KEY));
            capturedWorkerName.set(MDC.get(MdcKeys.WORKER_NAME));
            return WorkResult.completed(Map.of());
        }
    }

    @Test
    void execute_pushesJobKeyAndWorkerNameIntoMdc() {
        MdcProbingWorker probingWorker = new MdcProbingWorker(mapper, guard, meterRegistry);
        when(guard.check(any(), any())).thenReturn(false);

        probingWorker.execute(jobClient, activatedJob);

        // MDC values were set during doWork
        assertThat(probingWorker.capturedJobKey.get()).isEqualTo("123");
        assertThat(probingWorker.capturedWorkerName.get()).isEqualTo("MdcProbingWorker");

        // MDC cleared after execute returns
        assertThat(MDC.get(MdcKeys.JOB_KEY)).isNull();
        assertThat(MDC.get(MdcKeys.WORKER_NAME)).isNull();
    }

    @Test
    void execute_callsValidateBeforeDoWork() {
        AtomicBoolean doWorkCalled = new AtomicBoolean(false);

        BaseWorker<TestVars> validatingWorker = new BaseWorker<>(mapper, guard, meterRegistry) {
            @Override
            protected Class<TestVars> varsType() {
                return TestVars.class;
            }

            @Override
            protected void validate(TestVars vars) {
                throw new BusinessException("VALIDATION_FAILED", "bad input");
            }

            @Override
            protected WorkResult doWork(TestVars vars, ActivatedJob job) {
                doWorkCalled.set(true);
                return WorkResult.completed(Map.of());
            }
        };

        when(guard.check(any(), any())).thenReturn(false);
        validatingWorker.execute(jobClient, activatedJob);

        // validate threw BusinessException → doWork must NOT have been called
        assertThat(doWorkCalled.get()).isFalse();
        // throwError command should have been dispatched
        verify(jobClient).newThrowErrorCommand(activatedJob);
    }

    @Test
    void execute_translatesBusinessExceptionToThrowError() {
        worker.exceptionToThrow = new BusinessException("VALIDATION_FAILED", "bad");
        when(guard.check(any(), any())).thenReturn(false);

        // Must NOT throw to caller
        worker.execute(jobClient, activatedJob);

        verify(jobClient).newThrowErrorCommand(activatedJob);

        double count = meterRegistry.counter("framework_job_business_error_total",
                "type", "test-worker", "code", "VALIDATION_FAILED").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void execute_translatesNonRetryableToTechnicalFailure() {
        worker.exceptionToThrow = new NonRetryableException("SCHEMA_MISMATCH", "bad schema");
        when(guard.check(any(), any())).thenReturn(false);

        // Must NOT throw to caller
        worker.execute(jobClient, activatedJob);

        verify(jobClient).newThrowErrorCommand(activatedJob);

        double count = meterRegistry.counter("framework_job_failed_total",
                "type", "test-worker", "code", "TECHNICAL_FAILURE").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void execute_rethrowsRetryableException() {
        worker.exceptionToThrow = new RetryableException("TIMEOUT", "downstream timeout");
        when(guard.check(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> worker.execute(jobClient, activatedJob))
                .isInstanceOf(RetryableException.class);

        double count = meterRegistry.counter("framework_job_failed_total", "type", "test-worker").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void execute_appliesMapResponseTransform() {
        BaseWorker<TestVars> transformingWorker = new BaseWorker<>(mapper, guard, meterRegistry) {
            @Override
            protected Class<TestVars> varsType() {
                return TestVars.class;
            }

            @Override
            protected WorkResult doWork(TestVars vars, ActivatedJob job) {
                return WorkResult.completed(Map.of("original", "value"));
            }

            @Override
            protected Map<String, Object> mapResponse(WorkResult.Completed result) {
                Map<String, Object> enriched = new HashMap<>(result.variables());
                enriched.put("computed", "extra");
                return enriched;
            }
        };

        when(guard.check(any(), any())).thenReturn(false);
        transformingWorker.execute(jobClient, activatedJob);

        // The completeCommand should have been called with the transformed map
        verify(jobClient).newCompleteCommand(activatedJob);
        // Verify via the deep-stub chain that variables(...) was called with the enriched map
        verify(jobClient.newCompleteCommand(activatedJob))
                .variables(argThat((Map<String, Object> m) ->
                        "extra".equals(m.get("computed")) && "value".equals(m.get("original"))));
    }

    @Test
    void execute_handleExceptionCanTranslateToWorkResult() {
        RuntimeException rawEx = new RuntimeException("driver-specific error");

        BaseWorker<TestVars> recoveringWorker = new BaseWorker<>(mapper, guard, meterRegistry) {
            @Override
            protected Class<TestVars> varsType() {
                return TestVars.class;
            }

            @Override
            protected WorkResult doWork(TestVars vars, ActivatedJob job) {
                throw rawEx;
            }

            @Override
            protected WorkResult handleException(Exception ex, ActivatedJob job) {
                return WorkResult.completed(Map.of("recovered", true));
            }
        };

        when(guard.check(any(), any())).thenReturn(false);

        // Must NOT throw to caller — handleException returned a WorkResult
        recoveringWorker.execute(jobClient, activatedJob);

        verify(jobClient).newCompleteCommand(activatedJob);
        verify(jobClient.newCompleteCommand(activatedJob))
                .variables(argThat((Map<String, Object> m) -> Boolean.TRUE.equals(m.get("recovered"))));

        double count = meterRegistry.counter("framework_job_completed_total", "type", "test-worker").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void execute_handleExceptionNullRethrows() {
        // Default handleException returns null → framework should rethrow
        RuntimeException rawEx = new RuntimeException("unexpected error");
        worker.exceptionToThrow = rawEx;
        when(guard.check(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> worker.execute(jobClient, activatedJob))
                .isSameAs(rawEx);

        double count = meterRegistry.counter("framework_job_failed_total", "type", "test-worker").count();
        assertThat(count).isEqualTo(1.0);
    }
}
