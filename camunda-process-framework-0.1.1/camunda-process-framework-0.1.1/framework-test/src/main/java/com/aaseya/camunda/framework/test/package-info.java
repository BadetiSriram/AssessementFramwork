/**
 * Reusable test infrastructure for all services built on the Camunda process framework.
 *
 * <p>This module ships four categories of abstractions that consuming services import at
 * test scope:
 * <ul>
 *   <li><strong>{@code archunit}</strong> — ready-to-use {@link com.tngtech.archunit.lang.ArchRule}
 *       constants (see {@code ArchitectureRules}) that enforce layering, Camunda client
 *       encapsulation, domain isolation, REST-controller hygiene, and constructor-injection
 *       conventions. Consumers declare them with {@code @ArchTest} in their own
 *       {@code @AnalyzeClasses}-annotated test class.</li>
 *   <li><strong>{@code process}</strong> — {@code CamundaScenarioTestBase}, an abstract
 *       JUnit 5 base class annotated with {@code @CamundaSpringProcessTest} that pre-wires
 *       the {@code CamundaClient} and {@code CamundaProcessTestContext} fields, and provides
 *       convenience helpers for starting processes and asserting their completion.</li>
 *   <li><strong>{@code db}</strong> — {@code JdbcTemplateTestFactory}, a static factory that
 *       creates lightweight, idempotent H2 in-memory {@link org.springframework.jdbc.core.JdbcTemplate}
 *       instances in PostgreSQL-compatibility mode, suitable for testing repository and
 *       idempotency-guard logic without a live database.</li>
 *   <li><strong>{@code mdc}</strong> — {@code MdcAssertions}, AssertJ-style helpers for
 *       verifying and cleaning SLF4J MDC state, preventing log-context leakage between
 *       test cases.</li>
 * </ul>
 *
 * <p>This module contains <em>no</em> sample business logic, example scenarios, or domain
 * classes. Consumers supply their own BPMN files, variable maps, and scenario assertions in
 * their own service test modules.
 */
package com.aaseya.camunda.framework.test;
