package com.aaseya.camunda.framework.test.archunit;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Reusable ArchUnit rules enforcing the layered architecture conventions of all
 * Camunda-based services built on this framework.
 *
 * <h2>Usage</h2>
 * <p>In a consuming service, declare a JUnit 5 architecture test class and reference
 * the rules you want enforced:</p>
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.myorg.myservice")
 * class MyServiceArchitectureTest {
 *
 *     @ArchTest
 *     static final ArchRule layering =
 *         ArchitectureRules.WEB_AND_WORKERS_MUST_NOT_ACCESS_INFRASTRUCTURE;
 *
 *     @ArchTest
 *     static final ArchRule camundaBoundary =
 *         ArchitectureRules.ONLY_INFRASTRUCTURE_CAMUNDA_MAY_IMPORT_CAMUNDA_CLIENT;
 *
 *     @ArchTest
 *     static final ArchRule domainIsolation =
 *         ArchitectureRules.DOMAIN_MUST_NOT_IMPORT_SPRING_WEB_OR_CAMUNDA;
 *
 *     @ArchTest
 *     static final ArchRule txOnControllers =
 *         ArchitectureRules.REST_CONTROLLERS_MUST_NOT_BE_TRANSACTIONAL;
 *
 *     @ArchTest
 *     static final ArchRule entityExposure =
 *         ArchitectureRules.CONTROLLER_METHODS_MUST_NOT_EXPOSE_ENTITIES;
 *
 *     @ArchTest
 *     static final ArchRule constructorInjection =
 *         ArchitectureRules.USE_CONSTRUCTOR_INJECTION;
 * }
 * }</pre>
 *
 * <p>All rules are ready-to-use {@link ArchRule} constants. They can also be called
 * programmatically via {@code rule.check(importedClasses)}.</p>
 */
public final class ArchitectureRules {

    /**
     * Prevents instantiation — this class is a static-constant holder only.
     */
    private ArchitectureRules() {
        throw new AssertionError("ArchitectureRules must not be instantiated");
    }

    // -------------------------------------------------------------------------
    // Layering rules
    // -------------------------------------------------------------------------

    /**
     * Web controllers and Camunda job workers must not reach into the infrastructure
     * or repository layers directly.
     *
     * <p><strong>Why:</strong> The application layer (web/workers) must remain
     * ignorant of persistence and messaging infrastructure. All cross-layer
     * coordination happens via domain services or ports-and-adapters interfaces.
     * Bypassing this boundary leads to brittle tests and tightly coupled code that
     * cannot be swapped for different infrastructure implementations.</p>
     */
    public static final ArchRule WEB_AND_WORKERS_MUST_NOT_ACCESS_INFRASTRUCTURE =
            noClasses()
                    .that().resideInAnyPackage("..web..", "..workers..")
                    .should().accessClassesThat().resideInAnyPackage("..infrastructure..", "..repository..")
                    .as("Web controllers and workers must not access infrastructure or repository classes directly");

    /**
     * Only classes in a service's {@code infrastructure.camunda} package (or
     * framework internals) may import {@code io.camunda.client} types.
     *
     * <p><strong>Why:</strong> Camunda client access must be encapsulated behind the
     * infrastructure boundary. Application-layer code (web, domain, workers except
     * those inside {@code infrastructure.camunda}) must call Camunda indirectly via
     * the framework's {@code ProcessService} abstraction. This ensures that:
     * <ul>
     *   <li>The Camunda client is never accidentally wired into domain logic.</li>
     *   <li>Switching transport protocols (e.g., REST vs gRPC) is an infrastructure
     *       concern with zero application-layer impact.</li>
     *   <li>Tests can mock {@code ProcessService} without pulling in a live Camunda
     *       cluster.</li>
     * </ul></p>
     */
    public static final ArchRule ONLY_INFRASTRUCTURE_CAMUNDA_MAY_IMPORT_CAMUNDA_CLIENT =
            noClasses()
                    .that().resideOutsideOfPackages("..infrastructure.camunda..", "com.aaseya.camunda.framework..")
                    .should().dependOnClassesThat().resideInAPackage("io.camunda.client..")
                    .as("Only infrastructure.camunda packages (and framework internals) may import io.camunda.client types");

    // -------------------------------------------------------------------------
    // Domain isolation rules
    // -------------------------------------------------------------------------

    /**
     * Domain classes must not import Spring Web, Servlet, or Camunda types.
     *
     * <p><strong>Why:</strong> The domain model represents pure business logic and
     * should have no knowledge of HTTP or process-engine APIs. Keeping domain classes
     * framework-agnostic:
     * <ul>
     *   <li>Enables testing domain logic with plain unit tests — no Spring context
     *       needed.</li>
     *   <li>Allows the domain to be reused across different transport mechanisms
     *       (REST, gRPC, messaging) without modification.</li>
     *   <li>Prevents the "anemic domain model" anti-pattern where domain objects
     *       become simple data-transfer bags wired with framework annotations.</li>
     * </ul></p>
     */
    public static final ArchRule DOMAIN_MUST_NOT_IMPORT_SPRING_WEB_OR_CAMUNDA =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.web..", "jakarta.servlet..", "io.camunda..")
                    .as("Domain classes must not depend on Spring Web, Servlet, or Camunda types");

    // -------------------------------------------------------------------------
    // Controller rules
    // -------------------------------------------------------------------------

    /**
     * REST controllers must not be annotated with {@code @Transactional} at the
     * class level.
     *
     * <p><strong>Why:</strong> Placing {@code @Transactional} on a REST controller
     * conflates two distinct concerns — HTTP request handling and database transaction
     * management. Specifically:
     * <ul>
     *   <li>Transactions held open during HTTP I/O (serialisation, response writing)
     *       tie up database connections longer than necessary.</li>
     *   <li>It bypasses the service layer, making it trivial for future developers to
     *       add business logic directly in the controller while assuming transactional
     *       behaviour.</li>
     *   <li>Rollback semantics become unpredictable when exceptions are swallowed by
     *       Spring MVC's exception-handler chain before the transaction manager sees
     *       them.</li>
     * </ul>
     * Transactions belong on the service or use-case layer.</p>
     */
    public static final ArchRule REST_CONTROLLERS_MUST_NOT_BE_TRANSACTIONAL =
            noClasses()
                    .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                    .as("@RestController classes must not be annotated with @Transactional");

    /**
     * Methods on REST controllers must not expose or accept JPA {@code @Entity} objects
     * directly as parameters or return types.
     *
     * <p><strong>Why:</strong> Returning or accepting entity objects in the HTTP layer:
     * <ul>
     *   <li>Leaks persistence metadata (e.g., {@code @Id}, {@code @Column}) into
     *       the API contract, coupling clients to the database schema.</li>
     *   <li>Can trigger lazy-loading exceptions when Jackson tries to serialise
     *       uninitialised proxy collections after the session closes.</li>
     *   <li>Makes API versioning difficult — a schema change forces an API change.</li>
     * </ul>
     * Use dedicated DTO / record classes for the HTTP boundary instead.</p>
     */
    public static final ArchRule CONTROLLER_METHODS_MUST_NOT_EXPOSE_ENTITIES =
            methods()
                    .that().areDeclaredInClassesThat()
                    .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should(new ArchCondition<JavaMethod>("not expose @Entity types as parameters or return type") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            // Check return type
                            checkTypeForEntityAnnotation(method, method.getReturnType().toErasure(), events, "return type");
                            // Check each parameter type
                            method.getParameters().forEach(param ->
                                    checkTypeForEntityAnnotation(method, param.getType().toErasure(), events,
                                            "parameter #" + param.getIndex()));
                        }

                        private void checkTypeForEntityAnnotation(
                                JavaMethod method,
                                com.tngtech.archunit.core.domain.JavaClass type,
                                ConditionEvents events,
                                String location) {
                            boolean hasEntityAnnotation = type.getAnnotations().stream()
                                    .anyMatch(a -> a.getRawType().getFullName()
                                            .equals("jakarta.persistence.Entity"));
                            if (hasEntityAnnotation) {
                                events.add(SimpleConditionEvent.violated(method,
                                        String.format("Method '%s' in '%s' exposes @Entity type '%s' as %s",
                                                method.getName(),
                                                method.getOwner().getFullName(),
                                                type.getFullName(),
                                                location)));
                            }
                        }
                    })
                    .as("Controller methods must not expose @Entity types as parameters or return types");

    // -------------------------------------------------------------------------
    // Spring wiring rules
    // -------------------------------------------------------------------------

    /**
     * No field in any class may be annotated with {@code @Autowired} (field injection).
     *
     * <p><strong>Why:</strong> Field injection via {@code @Autowired} is discouraged
     * because:
     * <ul>
     *   <li>It makes the class impossible to instantiate in a plain unit test without
     *       a Spring context — constructor injection allows direct {@code new MyClass(dep)}
     *       in tests.</li>
     *   <li>It hides dependencies — a class with five {@code @Autowired} fields
     *       does not declare its contract in its constructor signature.</li>
     *   <li>It prevents marking injected fields {@code final}, breaking immutability
     *       guarantees and making thread-safety harder to reason about.</li>
     * </ul>
     * Always use constructor injection (or, for optional dependencies, {@code @Autowired}
     * on the constructor itself — which is permitted by this rule).</p>
     */
    public static final ArchRule USE_CONSTRUCTOR_INJECTION =
            noFields()
                    .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .as("Fields must not use @Autowired — use constructor injection instead");
}
