package com.aaseya.camunda.framework.starter.camunda;

import com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard;
import com.aaseya.camunda.framework.core.outbox.OutboxRelay;
import com.aaseya.camunda.framework.core.process.CamundaProcessService;
import com.aaseya.camunda.framework.core.process.ProcessService;
import com.aaseya.camunda.framework.core.worker.VariableMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies that {@link FrameworkCamundaAutoConfiguration} registers the correct beans
 * under the correct conditions.  Uses {@link ApplicationContextRunner} for lightweight
 * context construction — no full Spring Boot application, no Camunda cluster required.
 */
class FrameworkCamundaAutoConfigurationBeansTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FrameworkCamundaAutoConfiguration.class));

    /**
     * When a {@link CamundaClient} mock bean is provided, the auto-configuration must
     * register a {@link ProcessService} and the concrete type must be
     * {@link CamundaProcessService}.
     */
    @Test
    void processService_registeredWhenCamundaClientPresent() {
        runner
                .withBean(CamundaClient.class, () -> mock(CamundaClient.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ProcessService.class);
                    assertThat(context.getBean(ProcessService.class))
                            .isInstanceOf(CamundaProcessService.class);
                });
    }

    /**
     * {@link VariableMapper} must be registered because the starter provides its own
     * {@link ObjectMapper} via {@code frameworkObjectMapper()}, satisfying the
     * {@code @ConditionalOnBean(ObjectMapper.class)} gate without any external mock.
     */
    @Test
    void variableMapper_registeredWhenObjectMapperPresent() {
        runner
                .run(context ->
                        assertThat(context).hasSingleBean(VariableMapper.class)
                );
    }

    /**
     * When both {@link CamundaClient} and {@link JdbcTemplate} mock beans are provided,
     * the auto-configuration must register both {@link IdempotencyGuard} and
     * {@link OutboxRelay}; the starter's own {@link ObjectMapper} satisfies the
     * {@code ObjectMapper} condition without requiring an external mock.
     */
    @Test
    void idempotencyGuardAndOutboxRelay_registeredWhenJdbcTemplateAndClientPresent() {
        runner
                .withBean(CamundaClient.class, () -> mock(CamundaClient.class))
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyGuard.class);
                    assertThat(context).hasSingleBean(OutboxRelay.class);
                });
    }

    /**
     * When no {@link JdbcTemplate} is in the context, neither {@link IdempotencyGuard}
     * nor {@link OutboxRelay} should be auto-configured.
     */
    @Test
    void idempotencyGuardAndOutboxRelay_absentWhenJdbcTemplateNotPresent() {
        runner
                .withBean(CamundaClient.class, () -> mock(CamundaClient.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IdempotencyGuard.class);
                    assertThat(context).doesNotHaveBean(OutboxRelay.class);
                });
    }

    /**
     * When the consuming service registers its own {@link ProcessService} bean,
     * the {@code @ConditionalOnMissingBean} guard must yield to it and must not
     * register a second {@code ProcessService}.
     */
    @Test
    void processService_autoConfigYieldsToUserDefinedBean() {
        ProcessService customBean = mock(ProcessService.class);

        runner
                .withBean(CamundaClient.class, () -> mock(CamundaClient.class))
                .withBean(ProcessService.class, () -> customBean)
                .run(context -> {
                    assertThat(context).hasSingleBean(ProcessService.class);
                    assertThat(context.getBean(ProcessService.class)).isSameAs(customBean);
                });
    }

    /**
     * When {@code framework.camunda.multi-tenant=true}, the auto-configured
     * {@link CamundaProcessService} must expose {@code isMultiTenant() == true}.
     */
    @Test
    void processService_multiTenantFlag_isForwardedFromProperties() {
        runner
                .withBean(CamundaClient.class, () -> mock(CamundaClient.class))
                .withPropertyValues("framework.camunda.multi-tenant=true")
                .run(context -> {
                    ProcessService service = context.getBean(ProcessService.class);
                    assertThat(service).isInstanceOf(CamundaProcessService.class);
                    CamundaProcessService impl = (CamundaProcessService) service;
                    assertThat(impl.isMultiTenant()).isTrue();
                });
    }

    /**
     * When {@code framework.camunda.multi-tenant} is not set (default), the
     * {@link CamundaProcessService} must be constructed with {@code multiTenant=false}.
     */
    @Test
    void processService_multiTenantFlag_defaultsFalse() {
        runner
                .withBean(CamundaClient.class, () -> mock(CamundaClient.class))
                .run(context -> {
                    ProcessService service = context.getBean(ProcessService.class);
                    assertThat(service).isInstanceOf(CamundaProcessService.class);
                    CamundaProcessService impl = (CamundaProcessService) service;
                    assertThat(impl.isMultiTenant()).isFalse();
                });
    }

    /**
     * The starter must register a Jackson 2.x {@link ObjectMapper} bean when the application
     * context contains no user-defined {@code ObjectMapper}, configured with unknown-property
     * tolerance and {@code JavaTimeModule} support.
     */
    @Test
    void objectMapper_registeredWhenNoBeanExists() {
        runner
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertThat(mapper).isNotNull();
                    assertThat(mapper.getDeserializationConfig()
                            .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                            .isFalse();
                    assertThat(mapper.getRegisteredModuleIds())
                            .contains("jackson-datatype-jsr310");
                });
    }

    /**
     * When the consuming service supplies its own {@link ObjectMapper}, the
     * {@code @ConditionalOnMissingBean} guard on {@code frameworkObjectMapper()} must back off
     * and the user-defined bean must win.
     */
    @Test
    void objectMapper_userDefinedBeanWins() {
        runner
                .withBean(ObjectMapper.class, () -> {
                    var m = new ObjectMapper();
                    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
                    return m;
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertThat(mapper.getDeserializationConfig()
                            .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                            .isTrue();
                });
    }
}
