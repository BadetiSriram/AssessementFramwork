package com.aaseya.camunda.framework.starter.camunda;

import com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard;
import com.aaseya.camunda.framework.core.idempotency.JdbcIdempotencyGuard;
import com.aaseya.camunda.framework.core.outbox.JdbcOutboxRelay;
import com.aaseya.camunda.framework.core.outbox.OutboxRelay;
import com.aaseya.camunda.framework.core.process.CamundaProcessService;
import com.aaseya.camunda.framework.core.process.ProcessService;
import com.aaseya.camunda.framework.core.worker.VariableMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.client.CamundaClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring Boot auto-configuration entry point for the Camunda process framework.
 *
 * <p>This class is activated automatically when {@link CamundaClient} is present on the
 * classpath (i.e., when {@code camunda-spring-boot-starter} is a dependency of the
 * consuming service). It registers {@link FrameworkCamundaProperties} as a Spring bean so
 * that the {@code framework.camunda.*} property namespace is available for injection
 * throughout the application context, and wires the standard framework infrastructure
 * beans ({@link ProcessService}, {@link VariableMapper}, {@link IdempotencyGuard},
 * {@link OutboxRelay}).
 *
 * <p>Every bean is guarded by {@code @ConditionalOnMissingBean} so that consuming services
 * can override any single bean without disabling the rest.
 *
 * <p>The companion {@link CamundaSaasDefaultsEnvironmentPostProcessor} (registered separately
 * via {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports})
 * injects the lowest-priority {@code camunda.client.mode=saas} default before this
 * auto-configuration is evaluated, so services work out of the box against Camunda SaaS
 * without any explicit mode setting.
 */
@AutoConfiguration(afterName = {
        "io.camunda.client.spring.configuration.CamundaAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"
})
@ConditionalOnClass(CamundaClient.class)
@EnableConfigurationProperties(FrameworkCamundaProperties.class)
public class FrameworkCamundaAutoConfiguration {

    /**
     * Provides a Jackson 2.x {@link ObjectMapper} when none is present in the application
     * context, because Spring Boot 4.x auto-configures Jackson 3.x by default whereas
     * Camunda 8.9 continues to depend on Jackson 2.x internally.
     *
     * @return a pre-configured {@link ObjectMapper} with unknown-property tolerance enabled
     *         and {@link JavaTimeModule} registered for {@code java.time.*} serialisation
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper frameworkObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Registers the primary {@link ProcessService} implementation backed by the Camunda client.
     * Multi-tenant mode is controlled by {@code framework.camunda.multi-tenant} (default
     * {@code false}).
     *
     * @param client the {@link CamundaClient} provided by the Camunda Spring starter
     * @param props  the framework configuration properties
     * @return a {@link CamundaProcessService} wired with the deployment-level tenant flag
     */
    @Bean
    @ConditionalOnBean(CamundaClient.class)
    @ConditionalOnMissingBean
    public ProcessService processService(CamundaClient client, FrameworkCamundaProperties props) {
        return new CamundaProcessService(client, props.isMultiTenant());
    }

    /**
     * Registers the {@link VariableMapper} anti-corruption layer using the application's
     * {@link ObjectMapper} (provided by Spring Boot's Jackson auto-configuration).
     *
     * @param objectMapper the Jackson {@code ObjectMapper} from the application context
     * @return a {@code VariableMapper} ready for use by {@code BaseWorker} subclasses
     */
    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnMissingBean
    public VariableMapper variableMapper(ObjectMapper objectMapper) {
        return new VariableMapper(objectMapper);
    }

    /**
     * Registers the JDBC-backed {@link IdempotencyGuard} when a {@link JdbcTemplate} is
     * available in the application context.  Services that do not declare a datasource
     * will not receive this bean; they must supply their own {@code IdempotencyGuard}.
     *
     * @param jdbcTemplate the Spring JDBC template connected to the service database
     * @return a {@link JdbcIdempotencyGuard} backed by the {@code worker_execution} table
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public IdempotencyGuard idempotencyGuard(JdbcTemplate jdbcTemplate) {
        return new JdbcIdempotencyGuard(jdbcTemplate);
    }

    /**
     * Registers the JDBC-backed {@link OutboxRelay} when both a {@link JdbcTemplate} and a
     * {@link ProcessService} are available.  The relay polls the {@code process_outbox} table
     * on a schedule and dispatches commands via {@code ProcessService}; consuming services
     * must add {@code @EnableScheduling} to their Spring configuration.
     *
     * @param jdbcTemplate   the Spring JDBC template connected to the service database
     * @param processService the {@code ProcessService} used to dispatch outbox commands
     * @param objectMapper   the Jackson {@code ObjectMapper} for deserializing payloads
     * @return a {@link JdbcOutboxRelay} backed by the {@code process_outbox} table
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
    @ConditionalOnBean({ObjectMapper.class, JdbcTemplate.class, ProcessService.class})
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(JdbcTemplate jdbcTemplate,
                                   ProcessService processService,
                                   ObjectMapper objectMapper) {
        return new JdbcOutboxRelay(jdbcTemplate, processService, objectMapper);
    }
}
