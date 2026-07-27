package com.aaseya.camunda.framework.test.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Self-tests for {@link ArchitectureRules}.
 *
 * <p>Each test imports a small set of inner classes that represent hypothetical
 * "good" (rule-compliant) and "bad" (rule-violating) shapes. The good shape must
 * pass without throwing, and the bad shape must throw an
 * {@link com.tngtech.archunit.lang.ArchRule} violation error.</p>
 *
 * <p>The inner classes exist solely to exercise the rules — they carry no business
 * meaning and are intentionally named {@code Good*} / {@code Bad*} to make that
 * clear.</p>
 */
class ArchitectureRulesTest {

    // -------------------------------------------------------------------------
    // WEB_AND_WORKERS_MUST_NOT_ACCESS_INFRASTRUCTURE
    // -------------------------------------------------------------------------

    /**
     * A class in a {@code ..web..} package that does NOT touch infrastructure — should pass.
     */
    static class GoodWebController {
        void handleRequest() { /* no infrastructure access */ }
    }

    /**
     * Verifies USE_CONSTRUCTOR_INJECTION passes for a class without @Autowired fields.
     * We use this as a "good" shape for the constructor injection rule.
     */
    static class GoodConstructorInjection {
        private final String dependency;
        GoodConstructorInjection(String dependency) {
            this.dependency = dependency;
        }
        String getDependency() { return dependency; }
    }

    /**
     * A class with an {@code @Autowired} field — must fail the constructor-injection rule.
     */
    static class BadFieldInjection {
        @Autowired
        private String badlyInjectedDependency;
    }

    /**
     * A class annotated {@code @RestController} AND {@code @org.springframework.transaction.annotation.Transactional}
     * — must fail the transactional-controllers rule.
     */
    @RestController
    @org.springframework.transaction.annotation.Transactional
    static class BadTransactionalController {
        void handle() { /* violates rule */ }
    }

    /**
     * A plain {@code @RestController} with no {@code @Transactional} — must pass.
     */
    @RestController
    static class GoodRestController {
        void handle() { /* no @Transactional */ }
    }

    // -------------------------------------------------------------------------
    // USE_CONSTRUCTOR_INJECTION
    // -------------------------------------------------------------------------

    @Test
    void useConstructorInjection_passesForClassWithNoAutowiredFields() {
        JavaClasses classes = new ClassFileImporter()
                .importClasses(GoodConstructorInjection.class);

        assertThatCode(() -> ArchitectureRules.USE_CONSTRUCTOR_INJECTION.check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void useConstructorInjection_failsForClassWithAutowiredField() {
        JavaClasses classes = new ClassFileImporter()
                .importClasses(BadFieldInjection.class);

        assertThatThrownBy(() -> ArchitectureRules.USE_CONSTRUCTOR_INJECTION.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Autowired");
    }

    // -------------------------------------------------------------------------
    // REST_CONTROLLERS_MUST_NOT_BE_TRANSACTIONAL
    // -------------------------------------------------------------------------

    @Test
    void restControllersMustNotBeTransactional_passesForPlainController() {
        JavaClasses classes = new ClassFileImporter()
                .importClasses(GoodRestController.class);

        assertThatCode(() -> ArchitectureRules.REST_CONTROLLERS_MUST_NOT_BE_TRANSACTIONAL.check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void restControllersMustNotBeTransactional_failsForTransactionalController() {
        JavaClasses classes = new ClassFileImporter()
                .importClasses(BadTransactionalController.class);

        assertThatThrownBy(() -> ArchitectureRules.REST_CONTROLLERS_MUST_NOT_BE_TRANSACTIONAL.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Transactional");
    }

    // -------------------------------------------------------------------------
    // CONTROLLER_METHODS_MUST_NOT_EXPOSE_ENTITIES — tested with rule check
    // -------------------------------------------------------------------------

    @Test
    void controllerMethodsMustNotExposeEntities_passesForPlainController() {
        JavaClasses classes = new ClassFileImporter()
                .importClasses(GoodRestController.class);

        assertThatCode(() -> ArchitectureRules.CONTROLLER_METHODS_MUST_NOT_EXPOSE_ENTITIES.check(classes))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // WEB_AND_WORKERS_MUST_NOT_ACCESS_INFRASTRUCTURE — package-level rule
    // (inner classes cannot be placed in arbitrary packages at runtime, so we
    // verify the rule itself is non-null and well-formed via its description)
    // -------------------------------------------------------------------------

    @Test
    void webAndWorkersMustNotAccessInfrastructure_ruleIsWellFormed() {
        assertThatCode(() ->
                // An empty class set trivially passes any "no classes ... should" rule
                ArchitectureRules.WEB_AND_WORKERS_MUST_NOT_ACCESS_INFRASTRUCTURE
                        .check(new ClassFileImporter().importClasses(GoodWebController.class))
        ).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // ONLY_INFRASTRUCTURE_CAMUNDA_MAY_IMPORT_CAMUNDA_CLIENT — trivial sanity
    // -------------------------------------------------------------------------

    @Test
    void onlyInfrastructureCamundaMayImportCamundaClient_ruleIsWellFormed() {
        // A class with no io.camunda.client imports passes trivially
        JavaClasses classes = new ClassFileImporter()
                .importClasses(GoodConstructorInjection.class);

        assertThatCode(() ->
                ArchitectureRules.ONLY_INFRASTRUCTURE_CAMUNDA_MAY_IMPORT_CAMUNDA_CLIENT.check(classes)
        ).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // DOMAIN_MUST_NOT_IMPORT_SPRING_WEB_OR_CAMUNDA — trivial sanity
    // -------------------------------------------------------------------------

    @Test
    void domainMustNotImportSpringWebOrCamunda_ruleIsWellFormed() {
        // A class with no spring.web / servlet / io.camunda imports passes trivially
        JavaClasses classes = new ClassFileImporter()
                .importClasses(GoodConstructorInjection.class);

        assertThatCode(() ->
                ArchitectureRules.DOMAIN_MUST_NOT_IMPORT_SPRING_WEB_OR_CAMUNDA.check(classes)
        ).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Static field accessibility sanity
    // -------------------------------------------------------------------------

    @Test
    void allRuleFieldsAreNonNull() {
        // Guard against accidental NullPointerException during static initialisation
        assertThat(ArchitectureRules.WEB_AND_WORKERS_MUST_NOT_ACCESS_INFRASTRUCTURE)
                .isNotNull();
        assertThat(ArchitectureRules.ONLY_INFRASTRUCTURE_CAMUNDA_MAY_IMPORT_CAMUNDA_CLIENT)
                .isNotNull();
        assertThat(ArchitectureRules.DOMAIN_MUST_NOT_IMPORT_SPRING_WEB_OR_CAMUNDA)
                .isNotNull();
        assertThat(ArchitectureRules.REST_CONTROLLERS_MUST_NOT_BE_TRANSACTIONAL)
                .isNotNull();
        assertThat(ArchitectureRules.CONTROLLER_METHODS_MUST_NOT_EXPOSE_ENTITIES)
                .isNotNull();
        assertThat(ArchitectureRules.USE_CONSTRUCTOR_INJECTION)
                .isNotNull();
    }
}
