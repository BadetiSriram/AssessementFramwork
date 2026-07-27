/**
 * Spring Boot auto-configuration for JPA data access and Flyway schema migration
 * conventions shared across all Camunda process framework service consumers.
 *
 * <p>This package provides four key components: {@link com.aaseya.camunda.framework.starter.data.FrameworkDataProperties},
 * which exposes the {@code framework.data.*} configuration namespace for audit and
 * Flyway settings; {@link com.aaseya.camunda.framework.starter.data.FrameworkDataDefaultsEnvironmentPostProcessor},
 * which injects lowest-priority defaults for {@code spring.jpa.hibernate.ddl-auto},
 * {@code spring.jpa.open-in-view}, and Flyway baseline/validation flags so that
 * consuming services start with safe schema-management behaviour out of the box;
 * {@link com.aaseya.camunda.framework.starter.data.AuditColumnListener}, a reflection-based
 * JPA entity listener that stamps {@code createdAt}, {@code updatedAt}, {@code createdBy},
 * and {@code updatedBy} fields without requiring a common base class; and
 * {@link com.aaseya.camunda.framework.starter.data.FlywayNamingConventionValidator}, a
 * Flyway {@code Callback} that enforces the {@code V\d+(_\d+)*__[a-z0-9_]+.sql} naming
 * convention on all versioned migration scripts before each migration run, failing fast
 * when a violation is detected. All beans are conditional on the relevant classpath
 * entries ({@code jakarta.persistence.EntityManager} for JPA, {@code org.flywaydb.core.Flyway}
 * for the naming validator) and can be overridden or disabled per the standard Spring Boot
 * auto-configuration contract.
 */
package com.aaseya.camunda.framework.starter.data;
