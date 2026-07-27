package com.aaseya.orders.architecture;

import com.aaseya.camunda.framework.test.archunit.ArchitectureRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Wires the framework's six reusable architecture rules against this service's packages.
 *
 * <p>This is the payoff test: it proves the folder layout is correct — in particular that
 * the job worker sits in {@code infrastructure.camunda} (so it may import
 * {@code io.camunda.client}) and that nothing else does, that the controller is DTO-only
 * and non-transactional, that the domain is framework-pure, and that every class uses
 * constructor injection.
 */
@AnalyzeClasses(packages = "com.aaseya.orders")
class OrderArchitectureTest {

    @ArchTest
    static final ArchRule layering =
            ArchitectureRules.WEB_AND_WORKERS_MUST_NOT_ACCESS_INFRASTRUCTURE;

    @ArchTest
    static final ArchRule camundaBoundary =
            ArchitectureRules.ONLY_INFRASTRUCTURE_CAMUNDA_MAY_IMPORT_CAMUNDA_CLIENT;

    @ArchTest
    static final ArchRule domainIsolation =
            ArchitectureRules.DOMAIN_MUST_NOT_IMPORT_SPRING_WEB_OR_CAMUNDA;

    @ArchTest
    static final ArchRule txOnControllers =
            ArchitectureRules.REST_CONTROLLERS_MUST_NOT_BE_TRANSACTIONAL;

    @ArchTest
    static final ArchRule entityExposure =
            ArchitectureRules.CONTROLLER_METHODS_MUST_NOT_EXPOSE_ENTITIES;

    @ArchTest
    static final ArchRule constructorInjection =
            ArchitectureRules.USE_CONSTRUCTOR_INJECTION;
}
