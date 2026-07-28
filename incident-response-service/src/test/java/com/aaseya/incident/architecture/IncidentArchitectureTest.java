package com.aaseya.incident.architecture;

import com.aaseya.camunda.framework.test.archunit.ArchitectureRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Runs the framework's architecture rules over our packages. Mostly here to stop the Camunda
 * client leaking out of infrastructure.camunda and entities leaking out of controllers.
 */
@AnalyzeClasses(packages = "com.aaseya.incident")
class IncidentArchitectureTest {

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
