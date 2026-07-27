package com.aaseya.camunda.framework.core.process;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.client.api.response.PublishMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CamundaProcessService#publish(PublishMessageCommand)} and for the
 * {@link PublishMessageCommand} record itself.
 *
 * <p>The Camunda client is mocked at each individual step of the fluent builder chain
 * (Step1 → Step2 → Step3 → send → join) so that every option path can be verified in
 * isolation without requiring a running engine.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CamundaProcessServiceTest {

    // -------------------------------------------------------------------------
    // Mocks for the publish message fluent chain
    // -------------------------------------------------------------------------

    @Mock
    private CamundaClient camundaClient;

    @Mock
    private PublishMessageCommandStep1 step1;

    @Mock
    private PublishMessageCommandStep2 step2;

    @Mock
    private PublishMessageCommandStep3 step3;

    @SuppressWarnings("unchecked")
    @Mock
    private CamundaFuture<PublishMessageResponse> sendFuture;

    private CamundaProcessService service;

    /**
     * Wire the standard happy-path fluent chain:
     * {@code client.newPublishMessageCommand() → step1 → step2 → step3 → send → future}.
     * Individual tests override only the portion they need to assert.
     */
    @BeforeEach
    void setUp() {
        service = new CamundaProcessService(camundaClient);

        when(camundaClient.newPublishMessageCommand()).thenReturn(step1);
        when(step1.messageName(anyString())).thenReturn(step2);
        when(step2.correlationKey(anyString())).thenReturn(step3);
        when(step2.withoutCorrelationKey()).thenReturn(step3);

        // All optional step3 methods return step3 so chains keep working.
        when(step3.variables(any(Map.class))).thenReturn(step3);
        when(step3.timeToLive(any(Duration.class))).thenReturn(step3);
        when(step3.messageId(anyString())).thenReturn(step3);

        when(step3.send()).thenReturn(sendFuture);
        when(sendFuture.join()).thenReturn(mock(PublishMessageResponse.class));
    }

    // =========================================================================
    // PublishMessageCommand record validation
    // =========================================================================

    @Test
    void record_rejectsNullMessageName() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new PublishMessageCommand(null, "key", null, null, null));
    }

    @Test
    void record_rejectsBlankMessageName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new PublishMessageCommand("   ", "key", null, null, null))
                .withMessageContaining("messageName must not be blank");
    }

    @Test
    void of_factoryProducesBroadcastForm() {
        PublishMessageCommand cmd = PublishMessageCommand.of("MyMessage");

        assertThat(cmd.messageName()).isEqualTo("MyMessage");
        assertThat(cmd.correlationKey()).isNull();
        assertThat(cmd.variables()).isNull();
        assertThat(cmd.timeToLive()).isNull();
        assertThat(cmd.messageId()).isNull();
    }

    // =========================================================================
    // CamundaProcessService.publish() — happy-path and option branches
    // =========================================================================

    @Test
    void publish_sendsMessageNameAndCorrelationKey() {
        PublishMessageCommand cmd = new PublishMessageCommand(
                "OrderShipped", "ORDER-001", null, null, null);

        service.publish(cmd);

        verify(step1).messageName("OrderShipped");
        verify(step2).correlationKey("ORDER-001");
        verify(step3).send();
        verify(sendFuture).join();
    }

    @Test
    void publish_broadcastsWhenCorrelationKeyIsNull() {
        PublishMessageCommand cmd = PublishMessageCommand.of("BroadcastEvent");

        service.publish(cmd);

        verify(step2).withoutCorrelationKey();
        verify(step2, never()).correlationKey(anyString());
    }

    @Test
    void publish_omitsVariablesWhenNull() {
        PublishMessageCommand cmd = new PublishMessageCommand(
                "TestMessage", "KEY-1", null, null, null);

        service.publish(cmd);

        verify(step3, never()).variables(any(Map.class));
    }

    @Test
    void publish_omitsVariablesWhenEmpty() {
        PublishMessageCommand cmd = new PublishMessageCommand(
                "TestMessage", "KEY-1", Map.of(), null, null);

        service.publish(cmd);

        verify(step3, never()).variables(any(Map.class));
    }

    @Test
    void publish_includesVariablesWhenPresent() {
        Map<String, Object> vars = Map.of("orderId", "ORD-42", "amount", 100);
        PublishMessageCommand cmd = new PublishMessageCommand(
                "TestMessage", "KEY-1", vars, null, null);

        service.publish(cmd);

        verify(step3).variables(vars);
    }

    @Test
    void publish_includesTimeToLiveWhenPresent() {
        Duration ttl = Duration.ofMinutes(5);
        PublishMessageCommand cmd = new PublishMessageCommand(
                "TestMessage", "KEY-1", null, ttl, null);

        service.publish(cmd);

        verify(step3).timeToLive(ttl);
    }

    @Test
    void publish_omitsTimeToLiveWhenNull() {
        PublishMessageCommand cmd = new PublishMessageCommand(
                "TestMessage", "KEY-1", null, null, null);

        service.publish(cmd);

        verify(step3, never()).timeToLive(any(Duration.class));
    }

    @Test
    void publish_includesMessageIdWhenPresent() {
        PublishMessageCommand cmd = new PublishMessageCommand(
                "TestMessage", "KEY-1", null, null, "dedup-123");

        service.publish(cmd);

        verify(step3).messageId("dedup-123");
    }

    @Test
    void publish_omitsMessageIdWhenNull() {
        PublishMessageCommand cmd = new PublishMessageCommand(
                "TestMessage", "KEY-1", null, null, null);

        service.publish(cmd);

        verify(step3, never()).messageId(anyString());
    }

    @Test
    void publish_wrapsClientExceptionInProcessServiceException() {
        RuntimeException clientError = new RuntimeException("broker unavailable");
        when(sendFuture.join()).thenThrow(clientError);

        PublishMessageCommand cmd = new PublishMessageCommand(
                "TestMessage", "KEY-1", null, null, null);

        assertThatThrownBy(() -> service.publish(cmd))
                .isInstanceOf(ProcessServiceException.class)
                .hasMessageContaining("TestMessage")
                .hasCause(clientError);
    }

    @Test
    void publish_preservesCauseChainOnClientException() {
        IllegalStateException root = new IllegalStateException("network timeout");
        RuntimeException wrapper = new RuntimeException("send failed", root);
        when(sendFuture.join()).thenThrow(wrapper);

        PublishMessageCommand cmd = PublishMessageCommand.of("SomeEvent");

        assertThatThrownBy(() -> service.publish(cmd))
                .isInstanceOf(ProcessServiceException.class)
                .cause()
                .isSameAs(wrapper);
    }
}
