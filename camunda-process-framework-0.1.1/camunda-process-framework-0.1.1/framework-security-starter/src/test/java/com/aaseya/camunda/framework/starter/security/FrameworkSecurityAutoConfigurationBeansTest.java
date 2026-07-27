package com.aaseya.camunda.framework.starter.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-level bean tests for {@link FrameworkSecurityAutoConfiguration}.
 *
 * <p>Uses {@link ApplicationContextRunner} so no web server or Camunda components are
 * needed.  Each test verifies conditional bean registration and back-off behaviour.
 */
class FrameworkSecurityAutoConfigurationBeansTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FrameworkSecurityAutoConfiguration.class));

    // -------------------------------------------------------------------------
    // JwtAuthenticationConverter is registered by default
    // -------------------------------------------------------------------------

    @Test
    void jwtAuthenticationConverter_isRegistered() {
        runner.run(ctx ->
                assertThat(ctx).hasSingleBean(JwtAuthenticationConverter.class)
        );
    }

    // -------------------------------------------------------------------------
    // CorsConfigurationSource — absent by default, present when cors.enabled=true
    // -------------------------------------------------------------------------

    @Test
    void corsConfigurationSource_absentWhenCorsDisabled() {
        runner.run(ctx ->
                assertThat(ctx).doesNotHaveBean(CorsConfigurationSource.class)
        );
    }

    @Test
    void corsConfigurationSource_presentWhenCorsEnabled() {
        runner.withPropertyValues(
                "framework.security.cors.enabled=true",
                "framework.security.cors.allowed-origins=https://test.example.com"
        ).run(ctx ->
                assertThat(ctx).hasSingleBean(CorsConfigurationSource.class)
        );
    }

    // -------------------------------------------------------------------------
    // @ConditionalOnMissingBean back-off for JwtAuthenticationConverter
    // -------------------------------------------------------------------------

    @Test
    void userCanOverrideJwtAuthenticationConverter() {
        runner.withUserConfiguration(UserJwtConverterConfig.class)
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(JwtAuthenticationConverter.class);
                  // The registered bean must be the user-supplied one
                  assertThat(ctx.getBean(JwtAuthenticationConverter.class))
                          .isSameAs(ctx.getBean("userJwtConverter"));
              });
    }

    // -------------------------------------------------------------------------
    // @ConditionalOnMissingBean back-off for CorsConfigurationSource
    // -------------------------------------------------------------------------

    @Test
    void userCanOverrideCorsConfigurationSource() {
        runner.withUserConfiguration(UserCorsSourceConfig.class)
              .withPropertyValues("framework.security.cors.enabled=true")
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(CorsConfigurationSource.class);
                  assertThat(ctx.getBean(CorsConfigurationSource.class))
                          .isSameAs(ctx.getBean("userCorsSource"));
              });
    }

    // -------------------------------------------------------------------------
    // User-supplied override configurations
    // -------------------------------------------------------------------------

    @Configuration
    static class UserJwtConverterConfig {
        @Bean("userJwtConverter")
        public JwtAuthenticationConverter userJwtConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Configuration
    static class UserCorsSourceConfig {
        @Bean("userCorsSource")
        public CorsConfigurationSource userCorsSource() {
            return new UrlBasedCorsConfigurationSource();
        }
    }
}
