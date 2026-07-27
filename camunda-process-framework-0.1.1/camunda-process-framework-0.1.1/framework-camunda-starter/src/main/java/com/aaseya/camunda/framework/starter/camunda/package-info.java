/**
 * Spring Boot auto-configuration for Camunda 8.9 SaaS integration.
 *
 * <p>This package contains:
 * <ul>
 *   <li>{@link com.aaseya.camunda.framework.starter.camunda.FrameworkCamundaProperties} —
 *       {@code @ConfigurationProperties(prefix = "framework.camunda")} bean exposing worker
 *       tuning parameters (max-jobs-active, poll interval, retry back-off, default retries)
 *       with production-ready defaults.</li>
 *   <li>{@link com.aaseya.camunda.framework.starter.camunda.FrameworkCamundaAutoConfiguration} —
 *       {@code @AutoConfiguration} class that activates when {@code CamundaClient} is on the
 *       classpath and registers the properties bean into the application context.</li>
 *   <li>{@link com.aaseya.camunda.framework.starter.camunda.CamundaSaasDefaultsEnvironmentPostProcessor} —
 *       {@code EnvironmentPostProcessor} that injects {@code camunda.client.mode=saas} at the
 *       lowest property-source precedence, giving services a working SaaS default while
 *       allowing any explicit configuration to override it.</li>
 * </ul>
 *
 * <p>All Camunda client interaction is restricted to this starter and the
 * {@code infrastructure.camunda} sub-package of each consuming service (enforced by
 * ArchUnit rules shipped in {@code framework-core}).
 */
package com.aaseya.camunda.framework.starter.camunda;
