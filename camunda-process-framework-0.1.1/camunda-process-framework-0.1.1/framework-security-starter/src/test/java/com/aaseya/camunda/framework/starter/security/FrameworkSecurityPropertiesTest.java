package com.aaseya.camunda.framework.starter.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding tests for {@link FrameworkSecurityProperties}.
 *
 * <p>Uses {@link ApplicationContextRunner} to verify that properties are correctly
 * bound from the environment without starting a full Spring application context.
 */
class FrameworkSecurityPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(FrameworkSecurityProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    // -------------------------------------------------------------------------
    // Default values
    // -------------------------------------------------------------------------

    @Test
    void defaults_areAppliedCorrectly() {
        runner.run(ctx -> {
            FrameworkSecurityProperties props = ctx.getBean(FrameworkSecurityProperties.class);

            // CORS defaults
            assertThat(props.getCors().isEnabled()).isFalse();
            assertThat(props.getCors().getAllowedOrigins()).isEmpty();
            assertThat(props.getCors().getAllowedMethods())
                    .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
            assertThat(props.getCors().getAllowedHeaders()).containsExactly("*");
            assertThat(props.getCors().isAllowCredentials()).isFalse();
            assertThat(props.getCors().getMaxAge()).isEqualTo(Duration.ofHours(1));

            // JWT defaults
            assertThat(props.getJwt().getRolesClaim()).isEqualTo("realm_access.roles");
            assertThat(props.getJwt().getRolePrefix()).isEqualTo("ROLE_");
        });
    }

    // -------------------------------------------------------------------------
    // Binding from environment
    // -------------------------------------------------------------------------

    @Test
    void corsProperties_boundFromEnvironment() {
        runner.withPropertyValues(
                "framework.security.cors.enabled=true",
                "framework.security.cors.allowed-origins=https://app.example.com",
                "framework.security.cors.allowed-methods=GET,POST",
                "framework.security.cors.allowed-headers=Authorization,Content-Type",
                "framework.security.cors.allow-credentials=true",
                "framework.security.cors.max-age=PT30M"
        ).run(ctx -> {
            FrameworkSecurityProperties props = ctx.getBean(FrameworkSecurityProperties.class);

            assertThat(props.getCors().isEnabled()).isTrue();
            assertThat(props.getCors().getAllowedOrigins())
                    .containsExactly("https://app.example.com");
            assertThat(props.getCors().getAllowedMethods())
                    .containsExactlyInAnyOrder("GET", "POST");
            assertThat(props.getCors().getAllowedHeaders())
                    .containsExactlyInAnyOrder("Authorization", "Content-Type");
            assertThat(props.getCors().isAllowCredentials()).isTrue();
            assertThat(props.getCors().getMaxAge()).isEqualTo(Duration.ofMinutes(30));
        });
    }

    @Test
    void jwtProperties_boundFromEnvironment() {
        runner.withPropertyValues(
                "framework.security.jwt.roles-claim=roles",
                "framework.security.jwt.role-prefix=APP_"
        ).run(ctx -> {
            FrameworkSecurityProperties props = ctx.getBean(FrameworkSecurityProperties.class);

            assertThat(props.getJwt().getRolesClaim()).isEqualTo("roles");
            assertThat(props.getJwt().getRolePrefix()).isEqualTo("APP_");
        });
    }

    @Test
    void multipleAllowedOrigins_boundAsList() {
        runner.withPropertyValues(
                "framework.security.cors.enabled=true",
                "framework.security.cors.allowed-origins=https://a.example.com,https://b.example.com"
        ).run(ctx -> {
            FrameworkSecurityProperties props = ctx.getBean(FrameworkSecurityProperties.class);
            assertThat(props.getCors().getAllowedOrigins())
                    .containsExactlyInAnyOrder("https://a.example.com", "https://b.example.com");
        });
    }
}
