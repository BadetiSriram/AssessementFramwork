package com.aaseya.camunda.framework.starter.observability;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-style tests for {@link FrameworkObservabilityAutoConfiguration}
 * using {@link ApplicationContextRunner} to verify bean registration conditions.
 */
class FrameworkObservabilityAutoConfigurationBeansTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FrameworkObservabilityAutoConfiguration.class);

    /**
     * With default configuration the MDC filter registration bean must be present.
     */
    @Test
    void filterRegistrationBeanPresentWithDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            FilterRegistrationBean<?> registration =
                    context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(MdcCorrelationFilter.class);
        });
    }

    /**
     * When {@code framework.observability.mdc.enabled=false} the filter
     * registration bean must not be present.
     */
    @Test
    void filterRegistrationAbsentWhenMdcDisabled() {
        contextRunner
                .withPropertyValues("framework.observability.mdc.enabled=false")
                .run(context ->
                        assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    /**
     * When {@code jakarta.servlet.Filter} is not on the classpath, the servlet-dependent
     * filter registration must not be created.
     */
    @Test
    void filterRegistrationAbsentWhenServletApiAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Filter.class))
                .run(context ->
                        assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }
}
