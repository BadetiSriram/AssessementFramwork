package com.aaseya.camunda.framework.starter.camunda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Registers a lowest-priority property source that sets {@code camunda.client.mode=saas}
 * as a sensible default for services built on this framework.
 *
 * <p>This post-processor runs before the application context is refreshed, inserting a
 * {@link MapPropertySource} named {@code frameworkCamundaSaasDefaults} at the very end
 * of the environment's property source chain (via {@code addLast}). Because Spring
 * resolves properties from highest to lowest precedence, any value set explicitly by the
 * consuming service — whether in {@code application.yml}, {@code application.properties},
 * system properties, or environment variables — automatically takes priority over the
 * default injected here.
 *
 * <p>To override the default mode, set the property explicitly in the service:
 * <pre>{@code
 * camunda:
 *   client:
 *     mode: self-managed
 * }</pre>
 *
 * <p>The registration of this post-processor is driven by
 * {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports},
 * so no additional Spring Boot configuration is required in the consuming service.
 */
public class CamundaSaasDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "frameworkCamundaSaasDefaults";
    private static final String CAMUNDA_CLIENT_MODE_KEY = "camunda.client.mode";
    private static final String CAMUNDA_CLIENT_MODE_SAAS = "saas";

    /**
     * Adds the {@code camunda.client.mode=saas} default at the lowest precedence in the
     * given environment.
     *
     * @param environment the environment to post-process; never {@code null}
     * @param application the application being started; never {@code null}
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        Map<String, Object> defaults = Map.of(CAMUNDA_CLIENT_MODE_KEY, CAMUNDA_CLIENT_MODE_SAAS);
        MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, defaults);
        environment.getPropertySources().addLast(propertySource);
    }
}
