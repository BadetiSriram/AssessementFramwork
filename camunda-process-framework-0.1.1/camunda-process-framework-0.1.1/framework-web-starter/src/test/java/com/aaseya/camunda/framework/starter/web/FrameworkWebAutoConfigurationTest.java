package com.aaseya.camunda.framework.starter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApplicationContextRunner} tests for {@link FrameworkWebAutoConfiguration}.
 *
 * <p>The framework auto-configuration is loaded via {@link AutoConfigurations#of} so that
 * it fires <em>after</em> any user-defined configurations, allowing
 * {@code @ConditionalOnMissingBean} to see user beans before the framework default is
 * registered.  The conditional on {@code RestControllerAdvice} is satisfied because
 * {@code spring-boot-starter-web} is on the module's compile classpath.
 */
class FrameworkWebAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FrameworkWebAutoConfiguration.class));

    @Test
    @DisplayName("GlobalExceptionHandler bean is registered by default")
    void handlerRegisteredByDefault() {
        contextRunner.run(ctx ->
                assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test
    @DisplayName("GlobalExceptionHandler bean is absent when property is false")
    void handlerDisabledWhenPropertyFalse() {
        contextRunner
                .withPropertyValues("framework.web.exception-handler-enabled=false")
                .run(ctx ->
                        assertThat(ctx).doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Test
    @DisplayName("User-defined GlobalExceptionHandler takes precedence over framework default")
    void userDefinedHandlerWins() {
        contextRunner
                .withUserConfiguration(CustomHandlerConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(ctx.getBean(GlobalExceptionHandler.class))
                            .isInstanceOf(CustomGlobalExceptionHandler.class);
                });
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** Custom subclass that a consuming service might register. */
    static class CustomGlobalExceptionHandler extends GlobalExceptionHandler {
        // no additional behaviour needed for this test
    }

    /** Configuration that registers the custom handler before the auto-config fires. */
    @Configuration
    static class CustomHandlerConfiguration {

        @Bean
        public GlobalExceptionHandler myHandler() {
            return new CustomGlobalExceptionHandler();
        }
    }
}
