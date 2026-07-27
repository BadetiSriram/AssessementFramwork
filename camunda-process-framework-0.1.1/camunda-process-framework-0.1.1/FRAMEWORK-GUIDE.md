# camunda-process-framework — The Complete Guide

**Version:** `0.1.0-SNAPSHOT` (distributed in a directory labelled `0.1.1` — see §15.2)
**Group ID:** `com.aaseya.camunda`
**Stack:** Java 21 · Spring Boot 4.0.5 · Camunda 8.9.0 SaaS (REST) · PostgreSQL 16 / H2
**Maintainer:** `jain.sanjay@aaseya.com`

---

### Which document should I read?

| Document | Purpose | Read it when |
|---|---|---|
| `README.md` | Operator/consumer reference — build, run, configure, deploy | You need a command or a config key, fast |
| `DEPLOYMENT-LOCAL.md` | Local-development guide — three run paths plus IntelliJ/Eclipse setup | You are getting the scaffold running on your workstation. **Read §15.3 and §15.9 here first** — its instructions are correct but the shipped code fails them in six places |
| `UNDERSTANDING.md` | ~15-minute orientation walkthrough | You have never seen this repo and want the shape of it |
| **`FRAMEWORK-GUIDE.md`** (this file) | The exhaustive reference — what, why, where, when, what you gain, how every piece works, and what is still broken | You need to make a decision about this framework, adopt it, extend it, or explain it to someone |
| `CLAUDE.md` | Condensed engineering cheat-sheet for AI coding agents | You are configuring an agent to work in this repo |

**How to read this document.** Parts A and B are prose that a non-engineer can follow end to end. Parts C and D are code-level and assume Java/Spring familiarity. Every factual claim carries a `file:line` reference so it can be checked against the source.

---

## Table of contents

**Part A — Orientation**
1. [Executive summary](#1-executive-summary)
2. [What problem does this solve? (the *why*)](#2-what-problem-does-this-solve-the-why)
3. [Where and when to use it — and when not to](#3-where-and-when-to-use-it--and-when-not-to)
4. [What you achieve by adopting it](#4-what-you-achieve-by-adopting-it)

**Part B — How it fits together**
5. [Module map and dependency shape](#5-module-map-and-dependency-shape)
6. [The execution model — `BaseWorker<V>` in depth](#6-the-execution-model--baseworkerv-in-depth)
7. [The business-vs-technical failure rule](#7-the-business-vs-technical-failure-rule)
8. [The reliability patterns](#8-the-reliability-patterns)
9. [Architecture guardrails (ArchUnit)](#9-architecture-guardrails-archunit)

**Part C — Operating it**
10. [Configuration reference](#10-configuration-reference)
11. [Build, test and quality gates](#11-build-test-and-quality-gates)
12. [The CI/CD pipeline](#12-the-cicd-pipeline)
13. [Deployment](#13-deployment)

**Part D — Using and extending**
14. [Adopting it: the scaffold workflow](#14-adopting-it-the-scaffold-workflow)
15. [Known gaps, gotchas and roadmap](#15-known-gaps-gotchas-and-roadmap)

**Appendices** — [A: File inventory](#appendix-a--file-inventory) · [B: Framework DDL](#appendix-b--framework-ddl) · [C: Glossary](#appendix-c--glossary)

---
---

# Part A — Orientation

## 1. Executive summary

`camunda-process-framework` is a **reusable base framework** — a set of Java libraries plus a copy-me service scaffold — for building microservices that orchestrate business processes on **Camunda 8.9 SaaS**.

It is **not an application**. You cannot "run the business process" because there isn't one. It ships **zero BPMN diagrams, zero job workers, and zero domain classes**, and that absence is a deliberate design decision stated explicitly in `service-template/README.md:33-38`:

> No sample BPMN files, workers, domain aggregates, REST controllers, or state machines are provided. This framework delivers reusable infrastructure; your business logic is added by you downstream.

**The house analogy.** The framework is the foundation, plumbing, wiring standards, and building code. Each product team builds its own rooms — the BPMN diagrams, the workers, the domain rules — on top of it. Every house on the estate ends up with the same electrical standard, so an electrician can work on any of them.

**What it physically contains — eight Maven modules** (`pom.xml:34-43`):

| # | Module | Kind | One-line role |
|---|---|---|---|
| 1 | `framework-core` | Library | The patterns. `BaseWorker`, `ProcessService`, `VariableMapper`, `IdempotencyGuard`, `OutboxRelay`, `AuditableEntity`, exception hierarchy, MDC keys. No Spring auto-config. |
| 2 | `framework-camunda-starter` | Boot starter | Turns those patterns into injectable Spring beans. Defaults `camunda.client.mode=saas`. |
| 3 | `framework-security-starter` | Boot starter | OAuth2 resource server; maps a JWT claim path to `ROLE_*` authorities; optional CORS. |
| 4 | `framework-observability-starter` | Boot starter | Micrometer/Prometheus, OTel bridge, correlation-ID servlet filter, business-counter helper. |
| 5 | `framework-data-starter` | Boot starter | JPA + Flyway conventions; reflective audit-column stamping; migration-name validation. |
| 6 | `framework-web-starter` | Boot starter | `Response<T>` envelope + RFC 7807 `GlobalExceptionHandler`. |
| 7 | `framework-test` | Test library | Six ArchUnit rules, a Camunda scenario-test base class, H2/MDC test helpers. |
| 8 | `service-template` | Scaffold | A runnable, empty Boot app wiring the starters together. Copy this to start a new service. |

Plus non-Java assets: a hardened multi-stage `Dockerfile`, a generic Helm chart (`deploy/helm-chart/`), a nine-stage GitLab CI pipeline (`.gitlab-ci.yml`), and a bespoke BPMN reference-integrity checker (`tools/check-bpmn-integrity.sh`).

**The one sentence to remember:** *it exists so that the hard, easy-to-get-wrong parts of running business processes on a workflow engine — error classification, duplicate delivery, reliable messaging, observability, architectural discipline — are solved once, correctly, and inherited by every service rather than rebuilt badly in each one.*

---

## 2. What problem does this solve? (the *why*)

The honest way to explain the value is to describe what happens **without** it.

Suppose four teams each build a Camunda-orchestrated service independently. Every one of them must decide, from scratch:

- What happens when a job handler throws?
- What happens when Camunda delivers the same job twice?
- How does a domain event reliably become a Camunda message when the database commit and the engine call are two separate systems?
- How does a JWT role become a Spring authority?
- How does a log line in service C get correlated with the request that started in service A?
- What does an API error body look like?
- Where do audit columns get filled in?
- What stops a developer from calling the Camunda client directly out of a domain object?

Four teams, four different answers, four different sets of bugs, and no shared dashboard. The framework's purpose is to make those eight questions **pre-answered and non-negotiable**.

### 2.1 The five concrete failure modes it prevents

These are not abstractions — each maps to a specific mechanism in the code.

**① A business rule failure burning the technical retry budget and raising a false incident.**
Naively, "customer has insufficient funds" and "the database connection dropped" both surface as a thrown exception, and Camunda treats both identically: decrement the retry counter, retry, and when retries hit zero, raise an **incident** that pages a human. But insufficient funds is a *normal business outcome* that should route down an alternate path in the diagram — not wake anyone up at 3am.
`BaseWorker`'s catch ladder (`framework-core/.../worker/BaseWorker.java:213-261`) separates the two: business failures become `newThrowErrorCommand(errorCode)`, which a BPMN error boundary event catches; technical failures are re-thrown so the retry model applies. See §7.

**② A redelivered job double-charging a customer.**
Camunda's job delivery is at-least-once. A worker that completes its work but crashes before acknowledging will be handed the same job again. If that work was "charge the card", the customer is charged twice.
`IdempotencyGuard` (`framework-core/.../idempotency/JdbcIdempotencyGuard.java`) records `(business_key, element_id)` as a composite primary key in `worker_execution`. `BaseWorker` checks it automatically before invoking your code and silently completes replays (`BaseWorker.java:172-178`). See §8.1.

**③ A lost — or double-sent — "start the next step" message.**
Your service commits an order to its own database, then calls Camunda to publish a message. If the process dies between those two steps, the database and the engine permanently disagree. Retrying the engine call risks sending it twice.
`OutboxRelay` (`framework-core/.../outbox/JdbcOutboxRelay.java`) implements the transactional-outbox pattern: the intent to call Camunda is written to `process_outbox` **inside the same database transaction** as the domain change, and a separate scheduled poller dispatches it afterwards. Either both happen or neither does. See §8.2.

**④ Domain code reaching into `io.camunda.client` and becoming untestable.**
Once a domain aggregate imports the Camunda client, you cannot unit-test it without a live cluster or an elaborate mock, and switching transport (REST↔gRPC) becomes a domain-layer change.
ArchUnit rule 2 (`framework-test/.../ArchitectureRules.java:98-102`) fails the build if any class outside `..infrastructure.camunda..` touches `io.camunda.client..`. All engine access goes through the `ProcessService` facade. See §9.

**⑤ A BPMN step drawn on the diagram but never coded.**
The BPMN file says `<zeebe:taskDefinition type="reserve-inventory" />`. Nobody wrote `@JobWorker(type = "reserve-inventory")`. The service deploys cleanly, processes start cleanly, and then hang forever at that step with no error — because Camunda is patiently waiting for a worker that does not exist.
`tools/check-bpmn-integrity.sh` cross-references every task type in every BPMN file against every `@JobWorker` annotation in the Java sources, **in both directions**, and fails CI on either kind of orphan (`check-bpmn-integrity.sh:128-146`). See §12.2.

### 2.2 The second-order value

Beyond bug prevention, three things follow from having one framework instead of four conventions:

- **Uniform incident semantics.** An operator looking at Camunda Operate sees the same error-code vocabulary across every service, because `FrameworkException` forces a stable upper-case `errorCode` on every failure (`framework-core/.../exception/FrameworkException.java:43-47`) and that same string is simultaneously the BPMN error code, the Micrometer tag, and the API `errorCode` field.
- **One dashboard shape.** Every worker in every service emits `framework_job_completed_total`, `framework_job_business_error_total`, `framework_job_failed_total`, and `framework_job_replayed_total`, tagged by job type (`BaseWorker.java:34-37`). A single Grafana dashboard works for the whole estate.
- **Architecture enforced by a red build, not by code review.** Six ArchUnit rules run as ordinary JUnit tests. Layering violations fail CI rather than depending on a reviewer noticing.

---

## 3. Where and when to use it — and when not to

### 3.1 Where it fits

| Scenario | Why this framework suits it |
|---|---|
| **Multi-step business lifecycles** — loan origination, claims processing, onboarding, order fulfilment | The lifecycle *is* the BPMN diagram; each step is a worker; the framework supplies the step-execution skeleton |
| **Processes with human approval steps** | Camunda user tasks assigned to candidate groups that map 1:1 to identity-provider groups; `ProcessService.completeActiveUserTask` handles the eventually-consistent search index for you |
| **Cross-service sagas** | Orchestration-based: Camunda mediates every hop, services never call each other directly for saga steps, and rollback is a BPMN compensation boundary event plus a `<step>-compensate` worker rather than hand-written undo code |
| **Anything requiring an auditable process trail** | Camunda Operate gives the process-level trail; `AuditableEntity` gives the domain-level state-transition trail; the audit-column listener gives the row-level trail |
| **Estates of several services that must look and behave alike** | This is the framework's core payoff — it is worth materially less for a single service |

### 3.2 Where it does not fit

- **Simple CRUD services.** If there is no multi-step lifecycle, there is no process to orchestrate, and you inherit a Camunda dependency for nothing.
- **Camunda 7, or self-managed Camunda 8 with different client wiring.** The framework targets the Camunda 8.9 SaaS client (`CamundaClient`, not the older `ZeebeClient`) over REST. `camunda.client.mode=saas` is defaulted by an `EnvironmentPostProcessor`; a self-managed cluster requires overriding that and re-validating the auth path.
- **Non-Spring-Boot-4 stacks.** Every starter is Boot auto-configuration. There is no plain-Java or Quarkus path.
- **Teams that need Jackson 3.x as the application mapper.** The framework and Camunda 8.9 are on Jackson 2.x; see §15.1.

### 3.3 When — the version-pinning constraint

This is the single most important operational constraint, and it is stated in the parent POM itself (`pom.xml:8-14`):

> Spring Boot 4.0.5 — the version Camunda 8.9.0 was built and tested against (confirmed from its published POM). Pinned; upgrade only with explicit approval from the framework maintainer, since the Camunda / Spring Boot compatibility matrix is version-sensitive.

The pinned set is: **Java 21**, **Spring Boot 4.0.5**, **Camunda 8.9.0**, **Jackson 2.x**, PostgreSQL driver 42.7.7, Resilience4j 2.3.0, ArchUnit 1.3.0 (`pom.xml:45-55`).

These versions move **together or not at all**. A team that unilaterally bumps Spring Boot will find Camunda's auto-configuration silently mis-wiring. Version changes require maintainer approval — `jain.sanjay@aaseya.com`.

One deliberate exception worth knowing: Resilience4j ships no Boot-4-specific artifact, so the framework uses `resilience4j-spring-boot3`, justified in a POM comment on the grounds that Boot 4 preserves the same auto-configuration protocol (`pom.xml:76-78`).

---

## 4. What you achieve by adopting it

This is the payoff table. Column 2 is what a team building without the framework has to design, write, test, and maintain themselves.

### 4.1 Concern-by-concern

| Concern | What you'd otherwise build yourself | What the framework gives you |
|---|---|---|
| **Worker execution skeleton** | Per-worker boilerplate: deserialize variables, log, complete or fail the job, emit metrics — repeated and drifting in every worker | `BaseWorker<V>`: a `final execute()` template method. You implement `varsType()` and `doWork()` — roughly 15 lines instead of ~150 |
| **Error classification** | An ad-hoc `try/catch` per worker; inconsistent decisions about what retries and what routes | A fixed catch ladder mapping `BusinessException`/`RetryableException`/`NonRetryableException`/`RuntimeException` to the correct Camunda command (§7) |
| **Idempotent job replay** | A bespoke dedup table, or nothing (and a duplicate-charge incident six months later) | `IdempotencyGuard` + `worker_execution(business_key, element_id)` composite PK, invoked automatically |
| **Reliable engine messaging** | Hand-rolled outbox, or a dual-write bug you don't know you have | `OutboxRelay` + `process_outbox` + `FOR UPDATE SKIP LOCKED` poller safe across replicas |
| **Engine facade** | Camunda client calls sprayed across the codebase | `ProcessService` — 6 methods, one seam to mock in tests, ArchUnit-enforced |
| **Eventually-consistent user-task search** | A flaky test suite and intermittent "task not found" bugs | Bounded exponential backoff built into `CamundaProcessService.findActiveUserTaskKey` (200 ms × 2, 5 attempts) |
| **Variable binding** | Jackson config duplicated per worker; silent nulls when BPMN forgets a variable | `VariableMapper` with one central config plus a reflective required-component check that names the missing variable |
| **Domain state machines** | `if (status == X) status = Y;` scattered across services | `AuditableEntity<S>` — declare `allowedTransitions(from)`; illegal moves throw `IllegalStateTransitionException` |
| **JWT roles → authorities** | A custom converter per service, hardcoded to one realm | `JwtRealmRolesAuthenticationConverter` driven by a configurable dot-path claim (default `realm_access.roles`) |
| **CORS** | Copy-pasted config blocks | Property-driven `CorsConfigurationSource`, opt-in |
| **Log correlation** | Manual MDC pushes and leaked MDC on pooled threads | `MdcCorrelationFilter` (HTTP) + `BaseWorker`'s seven MDC keys (jobs), both with guaranteed cleanup |
| **Business metrics** | Free-form counter names; no cross-service dashboard | `FrameworkCounters` enforcing `<domain>_<state>_total` |
| **Audit columns** | `@PrePersist` boilerplate on every entity | `AuditColumnListener` stamping `createdAt`/`updatedAt`/`createdBy`/`updatedBy` reflectively |
| **Migration naming** | A README rule nobody follows | `FlywayNamingConventionValidator` — fails startup on a non-conforming filename |
| **API envelope** | Every endpoint shaped differently | `Response<T>` with `data` + `meta{correlationId, timestamp}` |
| **API errors** | Inconsistent error bodies; stack traces leaked to callers | RFC 7807 `ProblemDetail` mapping, with the generic handler deliberately withholding `ex.getMessage()` |
| **Architecture discipline** | Code review, and hope | Six ArchUnit rules that fail the build |
| **Process testing** | Bespoke harness per service | `CamundaScenarioTestBase` wrapping `@CamundaSpringProcessTest` |
| **Container image** | An unhardened `FROM openjdk` one-liner | Multi-stage layered build, non-root UID 1000, `HEALTHCHECK`, `MaxRAMPercentage=75` |
| **Kubernetes deploy** | A hand-written Deployment YAML per service | Generic Helm chart: probes, HPA, PDB, NetworkPolicy, ServiceMonitor, secret injection, preStop drain |
| **CI pipeline** | Assembled per repo | Nine-stage reference pipeline with `extends:` inheritance |
| **BPMN↔code integrity** | Nothing — a gap most teams never even identify | `check-bpmn-integrity.sh`, four checks, bidirectional |

### 4.2 What that adds up to

- **Time to first working service:** copy the scaffold, rename the package, add BPMN + workers. The infrastructure decisions are already made.
- **Consistency across the estate:** an engineer moving between services finds the same worker shape, the same error vocabulary, the same metric names, the same package layout.
- **Fewer production surprises in the specific categories that hurt most:** duplicate side effects, lost messages, false incidents, untraceable requests.
- **Architecture that survives staff turnover,** because it is asserted by tests rather than remembered by people.

**Be clear-eyed about the cost too.** You inherit a pinned version matrix you cannot unilaterally upgrade (§3.3), a Jackson 2.x/3.x split you must respect (§15.1), and a set of layering rules that will reject code your team might otherwise have written. The framework is opinionated by design; that is the trade.

---
---

# Part B — How it fits together

## 5. Module map and dependency shape

```
framework-core          ← pure patterns, no Spring auto-config.
                          Depends on io.camunda:camunda-client-java + Jackson 2.x
                          + spring-context/tx/jdbc + micrometer-core + slf4j.
  ↑ (brought in transitively by)
framework-camunda-starter   → ProcessService, VariableMapper, IdempotencyGuard,
                              OutboxRelay, Jackson 2.x ObjectMapper
framework-security-starter  → JwtAuthenticationConverter, optional CorsConfigurationSource
framework-observability-starter → MdcCorrelationFilter registration (+ FrameworkCounters helper)
framework-data-starter      → AuditColumnListener, FlywayNamingConventionValidator
framework-web-starter       → Response<T>, GlobalExceptionHandler
framework-test              → ArchitectureRules, CamundaScenarioTestBase,
                              JdbcTemplateTestFactory, MdcAssertions   (test scope)
service-template            → the runnable scaffold that wires them together
```

### 5.1 Why it is split this way

**Take only what you need.** A headless worker service — no HTTP endpoints — depends on `framework-camunda-starter` alone (which pulls `framework-core` transitively) and never loads the web or security machinery. A service that also exposes a REST API adds `framework-web-starter` and `framework-security-starter`. This is Spring Boot's idiomatic packaging: capabilities are opt-in per module, not a monolith you must swallow whole.

**Self-registration.** Each starter declares its auto-configuration class in
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
Adding the JAR to the classpath is the entire installation step.

**Environment post-processors.** Two starters — camunda and data — additionally ship a low-priority `EnvironmentPostProcessor` registered via `...boot.env.EnvironmentPostProcessor.imports`. These run **before** the application context is created and inject opinionated defaults:

- camunda: `camunda.client.mode=saas` (`CamundaSaasDefaultsEnvironmentPostProcessor`)
- data: `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false` (`FrameworkDataDefaultsEnvironmentPostProcessor`)

Because they are lowest-priority, a service's own `application.yml` still wins. You get safe defaults without losing control.

### 5.2 Module by module

#### `framework-core` — the patterns library

The only module with no Spring Boot dependency at all. Depends on `spring-context`, `spring-tx`, `spring-jdbc`, `jackson-databind`, `jackson-datatype-jsr310`, `camunda-client-java`, `micrometer-core`, `slf4j-api`, and Resilience4j (`framework-core/pom.xml:26-92`).

Contributes six package families:

| Package | Contents |
|---|---|
| `worker` | `BaseWorker<V>`, `WorkResult`, `VariableMapper`, `VariableBindingException` |
| `process` | `ProcessService` (port), `CamundaProcessService` (adapter), three command records, `ProcessServiceException` |
| `idempotency` | `IdempotencyGuard` (port), `JdbcIdempotencyGuard` |
| `outbox` | `OutboxRelay` (port), `JdbcOutboxRelay`, `OutboxEntry` |
| `audit` | `AuditableEntity<S>` |
| `exception` | `FrameworkException` → `BusinessException` / `TechnicalException` → `RetryableException`, `NonRetryableException`; plus `IllegalStateTransitionException`, `BusinessError` |
| `mdc` | `MdcKeys` — eight canonical log-field name constants |

*Does not:* register any bean, read any property, or know that Spring Boot exists.

#### `framework-camunda-starter` — auto-configuration

`FrameworkCamundaAutoConfiguration` is gated on `@ConditionalOnClass(CamundaClient.class)` — it activates only when the consuming service actually depends on `camunda-spring-boot-starter`. It registers five beans, **each independently guarded by `@ConditionalOnMissingBean`**, so a service can override exactly one without losing the rest:

| Bean | Extra conditions | Notes |
|---|---|---|
| `ObjectMapper` | `@ConditionalOnMissingBean(ObjectMapper.class)` | A **Jackson 2.x** mapper with `FAIL_ON_UNKNOWN_PROPERTIES=false` + `JavaTimeModule`, because Boot 4 auto-configures Jackson 3.x and Camunda 8.9 needs 2.x (`FrameworkCamundaAutoConfiguration.java:55-62`) |
| `ProcessService` | `@ConditionalOnBean(CamundaClient.class)` | Wired with `props.isMultiTenant()` |
| `VariableMapper` | `@ConditionalOnBean(ObjectMapper.class)` | |
| `IdempotencyGuard` | `@ConditionalOnBean(JdbcTemplate.class)` | Absent in services with no datasource — they must supply their own |
| `OutboxRelay` | `@ConditionalOnBean({ObjectMapper, JdbcTemplate, ProcessService})` | Requires `@EnableScheduling` in the consumer to actually poll |

*Does not:* register the `CamundaClient` itself — that is Camunda's own starter's job.

#### `framework-security-starter` — OAuth2 resource server

Gated on `org.springframework.security.oauth2.jwt.Jwt` being present. Registers:

- `JwtAuthenticationConverter` — built by `JwtRealmRolesAuthenticationConverter.newConverter(...)`, which walks a **dot-delimited claim path** (default `realm_access.roles`, the Keycloak shape) through the claims map, uppercases each role, and prefixes it (default `ROLE_`). Missing or malformed intermediate segments return an empty authority list rather than throwing (`JwtRealmRolesAuthenticationConverter.java:84-126`). No realm name, client ID, or issuer URI is hardcoded anywhere.
- `CorsConfigurationSource` — only when `framework.security.cors.enabled=true` **and** `jakarta.servlet.ServletRequest` is on the classpath.

*Does not:* provide a `SecurityFilterChain`. That is deliberate and documented at `FrameworkSecurityAutoConfiguration.java:33-49` — the framework will not guess your endpoint authorization rules. You must write the chain yourself and inject the converter into it. This surprises people; see §15.6.

#### `framework-observability-starter` — metrics, tracing, correlation

Registers exactly one bean: a `FilterRegistrationBean<MdcCorrelationFilter>` at `HIGHEST_PRECEDENCE + 100` mapped to `/*`, conditional on `jakarta.servlet.Filter` and on `framework.observability.mdc.enabled` (default true).

`MdcCorrelationFilter` reads `X-Correlation-Id` from the request (generating a UUID if absent and `generate-if-absent` is true), puts it in the MDC, **echoes it back as a response header**, optionally reads `X-Tenant-Id`, and removes both keys in a `finally` block so nothing leaks across pooled threads (`MdcCorrelationFilter.java:65-77`).

`FrameworkCounters` is a helper class, **not a bean** — it is deliberately not auto-registered because the domain prefix (`orders`, `claims`, …) is application-specific. Services declare their own `@Bean` (`FrameworkObservabilityAutoConfiguration.java:29-40`). It enforces the Prometheus-friendly name shape `<domain>_<state>_total` and offers six named convenience methods (`created`, `approved`, `rejected`, `completed`, `compensated`, `failed`) plus an arbitrary `increment(state, tags)`.

*Does not:* register a `MeterRegistry` (Actuator does) or an OTel tracer (`micrometer-tracing-bridge-otel` does).

#### `framework-data-starter` — JPA and Flyway conventions

Gated on `jakarta.persistence.EntityManager`. Registers:

- `AuditColumnListener` — a JPA entity listener that reflectively stamps `createdAt`/`updatedAt` (`Instant`) and `createdBy`/`updatedBy` (`String`) on `@PrePersist`/`@PreUpdate`. All reflection is defensive: a missing field is skipped, every exception is caught and logged at DEBUG, and an entity declaring none of the four fields is a no-op (`AuditColumnListener.java:122-194`). Consumers opt in per entity with `@EntityListeners(AuditColumnListener.class)`.
- `FlywayNamingConventionValidator` — a Flyway `Callback` on `BEFORE_MIGRATE` enforcing `^V\d+(_\d+)*__[a-z0-9_]+\.sql$`. `V1__Init.sql` (capital), `V1__addUserTable.sql` (camelCase), `v1__init.sql` (lowercase prefix) all fail startup with a `FlywayException` listing every offender. `R__` repeatable migrations are skipped (`FlywayNamingConventionValidator.java:169-193`).

#### `framework-web-starter` — REST conventions

- `Response<T>` — a record of `data` + `Meta(correlationId, timestamp)`. `Response.ok(data)` reads the correlation ID straight from the MDC, so controllers need no wiring when the observability filter is active (`Response.java:76-78`).
- `GlobalExceptionHandler` — a `@RestControllerAdvice` mapping six exception categories to RFC 7807 `ProblemDetail` bodies. Every response carries `correlationId` and `timestamp` properties. Disable with `framework.web.exception-handler-enabled=false`, or simply define your own bean — the auto-configuration backs off.

Brings `spring-boot-starter-validation` transitively.

#### `framework-test` — test support

Consumed at `<scope>test</scope>`. Contributes `ArchitectureRules` (six `ArchRule` constants, §9), `CamundaScenarioTestBase` (an `@CamundaSpringProcessTest`-annotated abstract class pre-wiring `CamundaClient` and `CamundaProcessTestContext`, plus a `startProcess(bpmnProcessId, vars)` helper), `JdbcTemplateTestFactory` (H2-backed `JdbcTemplate`s), and `MdcAssertions` (helpers that catch leaked MDC context).

Note the scope contract at `CamundaScenarioTestBase.java:40-45`: the base class provides harness infrastructure only. BPMN deployments, variable maps, and assertions belong in the consuming service's own tests.

#### `service-template` — the scaffold

An `@SpringBootApplication` + `@EnableScheduling` class (`Application.java:14-16` — the annotation is mandatory or the outbox poller never fires), `application.yml` driven entirely by environment variables, five profile overlays, and `V1__framework_tables.sql`. Depends on `framework-camunda-starter` and `framework-core` explicitly, plus Boot's web/JPA/validation/actuator starters, Postgres at runtime scope, H2 at test scope, and Flyway (`service-template/pom.xml:26-101`).

Note it does **not** depend on `framework-web-starter`, `framework-security-starter`, `framework-observability-starter`, or `framework-data-starter` — a consuming team adds whichever it needs.

---

## 6. The execution model — `BaseWorker<V>` in depth

`framework-core/src/main/java/com/aaseya/camunda/framework/core/worker/BaseWorker.java` is the heart of the framework. Read this section carefully; everything else follows from it.

### 6.1 The shape

`BaseWorker<V>` is a **template method**. It fixes the algorithm and lets subclasses fill in named holes.

**You must implement two methods:**

| Method | Purpose |
|---|---|
| `Class<V> varsType()` (`:62`) | Class token for the record holding this job's input variables |
| `WorkResult doWork(V vars, ActivatedJob job)` (`:74`) | The actual work. This is the only place your business logic goes |

**You may override four more:**

| Hook | Default | Override when |
|---|---|---|
| `void validate(V vars)` (`:84`) | no-op | You want pre-work validation; throwing `BusinessException` here becomes a BPMN error event |
| `Map<String,Object> mapResponse(Completed)` (`:96`) | returns `result.variables()` | You need to inject computed fields or strip secrets before writing variables back |
| `WorkResult handleException(Exception, ActivatedJob)` (`:112`) | returns `null` → rethrow | You want to demote a specific driver exception to a `WorkResult` instead of burning a retry |
| `String workerType(ActivatedJob)` (`:123`) | `job.getType()` | You want a clearer metrics label |

**You cannot override `execute()`.** It is declared `final` (`:135`) precisely so framework concerns cannot be accidentally bypassed.

### 6.2 What you write vs what you inherit

A complete worker looks like this:

```java
@Component
public class ReserveInventoryWorker extends BaseWorker<ReserveInventoryVars> {

    private final InventoryService inventory;

    public ReserveInventoryWorker(VariableMapper mapper, IdempotencyGuard guard,
                                  MeterRegistry registry, InventoryService inventory) {
        super(mapper, guard, registry);
        this.inventory = inventory;
    }

    @Override protected Class<ReserveInventoryVars> varsType() {
        return ReserveInventoryVars.class;
    }

    @Override protected WorkResult doWork(ReserveInventoryVars vars, ActivatedJob job) {
        if (!inventory.hasStock(vars.sku(), vars.qty())) {
            return WorkResult.businessError("OUT_OF_STOCK", "SKU " + vars.sku() + " unavailable");
        }
        String reservationId = inventory.reserve(vars.sku(), vars.qty());
        return WorkResult.completed(Map.of("reservationId", reservationId));
    }

    @JobWorker(type = "reserve-inventory", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) { execute(client, job); }
}
```

Roughly 15 meaningful lines. Everything below — variable binding, MDC, idempotency, command dispatch, metrics, cleanup — is inherited.

Two details are load-bearing and easy to get wrong:
- **`autoComplete = false`** is mandatory. The framework issues the complete/throw-error command itself; letting Camunda auto-complete would double-dispatch.
- **The `type` string must match the BPMN `<zeebe:taskDefinition type="...">` exactly.** This is what `check-bpmn-integrity.sh` verifies (§12.2).

### 6.3 `execute()` step by step

```
                    ┌─────────────────────────────────────┐
Camunda delivers →  │  @JobWorker method → execute()      │
   an ActivatedJob  └──────────────┬──────────────────────┘
                                   ▼
   ① Bind variables      getVariablesAsType(V)  ──fail──▶ mapper.map(json, V)
                                   │                          └─fail─▶ throw (technical)
                                   ▼
   ② Push MDC            7 keys: processInstanceKey, elementId, jobType,
                         jobKey, workerName, [tenantId], [businessKey]
                                   ▼
   ③ Idempotency         businessKey present && guard.check(key, elementId)?
                            └─ yes ─▶ complete silently, count "replayed", RETURN
                                   ▼
   ④ validate(vars)      then doWork(vars, job)
                                   ▼
   ⑤ Dispatch on WorkResult
        Completed      ─▶ newCompleteCommand(vars) + guard.record(...) + count "completed"
        BusinessError  ─▶ newThrowErrorCommand(code, msg)  + count "business_error"
        Compensated    ─▶ newCompleteCommand()             + count "completed"
                                   ▼
   ⑥ finally            remove all 7 MDC keys
```

#### ① Variable binding — a two-tier fallback (`:139-150`)

```java
try {
    vars = job.getVariablesAsType(varsType());
} catch (Exception primary) {
    try {
        vars = mapper.map(job.getVariables(), varsType());   // JSON-string path
    } catch (Exception fallback) {
        log.error(...);
        throw new RuntimeException("Variable binding failed: " + primary.getMessage(), primary);
    }
}
```

The primary path uses Camunda's own typed accessor. If that fails — a common cause is Camunda's internal mapper choking on a shape `VariableMapper`'s laxer configuration accepts — the framework re-tries through its own mapper against the raw JSON string. Only if **both** fail does it throw, and it throws a plain `RuntimeException` (i.e. it is treated as a **technical** failure and burns a retry).

Note the binding happens **before** the MDC is pushed, so a binding failure log line will not carry the job context fields.

#### ② MDC context (`:152-168`)

Seven keys, all defined as constants in `MdcKeys.java`: `processInstanceKey`, `elementId`, `jobType`, `jobKey`, `workerName`, plus `tenantId` when the job carries one and `businessKey` when it can be extracted. Every log line emitted anywhere inside `doWork()` inherits these fields.

#### ③ Business key extraction and idempotency (`:172-178`, `:278-292`)

`extractBusinessKey()` is **convention-based**: it parses the job's variables JSON and reads a single top-level field named `businessKey`. If it is absent, null, or unparseable, the method returns `null` and **idempotency is silently skipped for that job**. Nothing warns you.

When a business key *is* present:

```java
if (businessKey != null && guard.check(businessKey, job.getElementId())) {
    log.info("Replayed job detected ... — completing silently");
    client.newCompleteCommand(job).send().join();
    meterRegistry.counter("framework_job_replayed_total", "type", type).increment();
    return;
}
```

The replay is completed without re-running your code. Watch `framework_job_replayed_total` — a nonzero rate is normal (at-least-once delivery); a *rising* rate is a signal.

#### ⑤ Result dispatch (`:187-211`)

`WorkResult` is a **sealed interface** permitting exactly three records (`WorkResult.java:12-13`), so the outcome space is closed and exhaustive:

| Result | Camunda command | Side effects |
|---|---|---|
| `Completed(variables)` | `newCompleteCommand(job).variables(mapResponse(result))` | `guard.record(businessKey, elementId, null)`; `framework_job_completed_total` |
| `BusinessError(errorCode, errorMessage)` | `newThrowErrorCommand(job).errorCode(...).errorMessage(...)` | `framework_job_business_error_total{type, code}` |
| `Compensated()` | `newCompleteCommand(job)` — no variables | `framework_job_completed_total` |

`Completed` defensively wraps its variable map as an unmodifiable copy in a compact constructor, and treats `null` as empty (`WorkResult.java:23-26`). Static factories `completed()`, `completed(vars)`, `businessError(code, msg)`, `compensated()` keep call sites readable.

**Note:** `guard.record(...)` runs only on the `Completed` path. A `BusinessError` outcome is *not* recorded as executed — which is correct, since the process is about to take a different route entirely.

#### The catch ladder (`:213-261`)

Order matters; this is the framework's central decision encoded as control flow.

| Caught | Action | Camunda effect |
|---|---|---|
| `BusinessException` | `newThrowErrorCommand(be.errorCode(), be.errorMessage())`; count business error | Routed by a BPMN error boundary event. **No retry consumed.** |
| `RetryableException` | count failure; **`throw re`** | Camunda decrements retries; at zero, raises an **incident** |
| `NonRetryableException` | `newThrowErrorCommand("TECHNICAL_FAILURE", …)`; count failure with `code=TECHNICAL_FAILURE` | Routed by BPMN. Retrying would not help, so the retry budget is not burned |
| `RuntimeException` (anything else) | call `handleException(ex, job)`. If it returns `null` → count failure and **rethrow**. Otherwise dispatch the returned `WorkResult` **inline** | Default is retry-then-incident |

The inline dispatch in the fallback branch (`:246-261`) is deliberate and carries an explanatory comment: it must not re-enter `execute()`, because that would re-run `validate()` and `doWork()` — re-executing side effects that already happened.

#### ⑥ Cleanup (`:262-271`)

The `finally` block removes all seven MDC keys unconditionally — including `tenantId` and `businessKey`, which may never have been set. `MDC.remove` on an absent key is a no-op, so this is safe and guarantees nothing leaks onto the next job on a pooled thread.

### 6.4 The four metrics

| Counter | Tags | Incremented when |
|---|---|---|
| `framework_job_completed_total` | `type` | `Completed` or `Compensated` dispatched |
| `framework_job_business_error_total` | `type`, `code` | `BusinessError` returned, or `BusinessException`/`NonRetryableException` caught |
| `framework_job_failed_total` | `type` (+ `code` for non-retryable) | `RetryableException`, `NonRetryableException`, or unhandled `RuntimeException` |
| `framework_job_replayed_total` | `type` | Idempotency guard short-circuited a replay |

---

## 7. The business-vs-technical failure rule

This is the framework's central design rule, and the thing teams most often get wrong. It deserves its own section.

**The principle:** a failure is either part of the business flow or orthogonal to it, and the two must be routed differently.

- A **business failure** — "insufficient funds", "customer not eligible", "document rejected" — is a *normal, expected* outcome. It has a place on the diagram. It must **not** consume the retry budget or raise an incident.
- A **technical failure** — "the database timed out", "the downstream API returned 503" — is *abnormal*. Retrying might fix it. If retries run out, a human should be told.

### 7.1 The decision table

| Symptom | You return/throw | Camunda does | Operator sees | HTTP layer returns |
|---|---|---|---|---|
| Domain rule violated | `WorkResult.businessError(code, msg)` or `throw BusinessException` | `newThrowErrorCommand(code)` → BPMN error boundary event | Nothing — the process took an alternate path, by design | **422** + `errorCode` |
| Transient infra failure | `throw RetryableException` | Retry count decremented; eventually an **incident** | An incident in Operate, with the message | **503** + `Retry-After: 5` |
| Permanent infra failure (misconfiguration, schema mismatch) | `throw NonRetryableException` | `newThrowErrorCommand("TECHNICAL_FAILURE")` | Routed by BPMN; retries not wasted | **500** + `errorCode` |
| Anything unclassified | any other `RuntimeException` | `handleException()` hook, else retry → incident | An incident | **500**, message withheld |

The HTTP column comes from `GlobalExceptionHandler` (`framework-web-starter/.../GlobalExceptionHandler.java`), which handles the same exception types on the inbound REST side. Spring MVC dispatches most-specific first; the class documents its own intended ordering at `:42-53`. Note the deliberate security choice in the last-resort handler (`:236-256`): the generic `Exception` handler logs the full exception but returns only `"An unexpected error occurred."`, so internal class and field names never reach an external caller.

Validation failures are handled separately: `MethodArgumentNotValidException` → 400 with a `fieldErrors` array; `ConstraintViolationException` → 400 with a `violations` array.

### 7.2 One error code, three consumers

`FrameworkException` is the abstract root of the whole hierarchy and enforces a **non-null, non-blank `errorCode`** and `errorMessage` on every instance (`FrameworkException.java:43-62`, `:84-90`):

```
FrameworkException (abstract)
├── BusinessException              → BPMN error event / HTTP 422
└── TechnicalException (abstract)
    ├── RetryableException         → rethrow, retry, incident / HTTP 503
    └── NonRetryableException      → BPMN error "TECHNICAL_FAILURE" / HTTP 500
```

(`IllegalStateTransitionException` — thrown by `AuditableEntity` — and the `BusinessError` type live in the same package.)

That single `errorCode` string is used in three places at once:

1. as the **BPMN error code** the boundary event matches on,
2. as the **Micrometer tag** `code` on the business-error counter,
3. as the **`errorCode` property** on the RFC 7807 API response.

This is why the Javadoc insists the code be stable and upper-case (`FrameworkException.java:25-27`) — it is a cross-system contract, not a log string. Changing one silently breaks a BPMN route, a dashboard, and an API consumer simultaneously.

---

## 8. The reliability patterns

Both database-backed patterns use the two tables created by `service-template/src/main/resources/db/migration/V1__framework_tables.sql`. Consumer migrations start at `V2__`.

### 8.1 Idempotency — `IdempotencyGuard`

```sql
CREATE TABLE worker_execution (
    business_key   VARCHAR(200) NOT NULL,
    element_id     VARCHAR(200) NOT NULL,
    completed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result_hash    VARCHAR(200),
    PRIMARY KEY (business_key, element_id)
);
```

The composite primary key is the whole idea: *this business entity has already been through this BPMN step*. The `element_id` component matters — the same order passing through `reserve-inventory` and `charge-card` yields two distinct rows, so each step is guarded independently.

`JdbcIdempotencyGuard` is two SQL statements: a `COUNT(*)` check and an `INSERT ... ON CONFLICT DO NOTHING`. The `ON CONFLICT` clause means concurrent inserts are safe without any application-level locking (`JdbcIdempotencyGuard.java:26-28`).

**Caveats worth knowing:**
- Guarding is entirely opt-in *by data*: no `businessKey` variable in the job payload means no guarding, silently (§6.3).
- `check` and `record` are separate calls, not atomic. `record` runs only *after* the complete command succeeds (`BaseWorker.java:189-193`), so a crash between the two leaves the job unrecorded and a genuine replay will re-execute. This is the correct trade — recording first would risk skipping work that never happened — but it means the guard reduces duplicate execution rather than eliminating it. Design `doWork()` to be idempotent where the stakes are high.
- `result_hash` is accepted by the interface but `BaseWorker` always passes `null`.

### 8.2 Transactional outbox — `OutboxRelay`

```sql
CREATE TABLE process_outbox (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(200) NOT NULL,
    kind           VARCHAR(20)  NOT NULL,   -- 'START' | 'MESSAGE'
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at  TIMESTAMP
);
CREATE INDEX idx_process_outbox_undispatched
    ON process_outbox (created_at) WHERE dispatched_at IS NULL;
```

**Write path.** Instead of calling Camunda directly, call `outboxRelay.publishStart(cmd)` or `publishCorrelate(cmd)` **inside your existing database transaction**. It serializes the command to JSON and inserts a row. Because it is the same transaction as your domain change, the two commit or roll back together — the dual-write problem disappears.

**Dispatch path.** A scheduled poller (`JdbcOutboxRelay.poll()`, `:119-139`):

```sql
SELECT id, aggregate_type, aggregate_id, kind, payload, created_at
FROM process_outbox
WHERE dispatched_at IS NULL
ORDER BY created_at
LIMIT 100
FOR UPDATE SKIP LOCKED
```

`FOR UPDATE SKIP LOCKED` is what makes multi-replica deployment safe: replica B skips rows replica A has locked instead of blocking on them. `LIMIT 100` bounds each batch.

Each row is dispatched via `ProcessService` and then marked `dispatched_at = now()`. A dispatch failure is logged and the row is **left undispatched**, so the next poll retries it (`:133-137`). There is no dead-letter path and no attempt counter — a permanently failing row will be retried forever, every poll interval. Worth monitoring.

**Two gotchas, both real:**
- **`@EnableScheduling` is mandatory.** `poll()` is annotated `@Scheduled(fixedDelayString = "${framework.outbox.poll-interval:PT2S}")`, but `@Scheduled` does nothing unless scheduling is enabled on the application class. Omit it and the poller silently never runs; rows accumulate; nothing errors. The scaffold has it (`Application.java:15`) — keep it.
- **`FOR UPDATE SKIP LOCKED` is Postgres-specific.** H2 in `MODE=PostgreSQL` accepts most Postgres syntax, but exercise `poll()` against real Postgres (Testcontainers) when in doubt.

Also note `payload` is `TEXT`, not `jsonb`, purely so the same migration runs on both H2 and Postgres. Production deployments needing native JSON operators can migrate it later (`V1__framework_tables.sql:1-3`).

### 8.3 State machine — `AuditableEntity<S extends Enum<S>>`

A domain aggregate extends it and supplies four methods: `allowedTransitions(from)`, `getStatus()`, `setStatus(s)`, `appendAuditNote(note, from, to)`.

`transition(to, note)` (`AuditableEntity.java:61-71`) does exactly three things, in order:

1. Look up `allowedTransitions(getStatus())`. If the set is `null` or does not contain `to` → throw `IllegalStateTransitionException` **before any mutation**.
2. `setStatus(to)`.
3. `appendAuditNote(note, from, to)` — in the same transaction as the status change.

A `null` or empty permitted-set always rejects, so a state you forgot to map is a closed door rather than an open one. The Javadoc recommends mapping `IllegalStateTransitionException` to HTTP 409 at the API boundary (`:59`).

### 8.4 Engine facade — `ProcessService`

Six methods (`ProcessService.java`):

| Method | Purpose |
|---|---|
| `long start(StartProcessCommand)` | Start an instance of the latest version; returns the process instance key |
| `void correlate(CorrelateMessageCommand)` | Correlate a message to a **specific waiting instance** |
| `void publish(PublishMessageCommand)` | **Broadcast** a message — any subscribed instance may consume it, including a message-start event that correlates against no instance |
| `void completeActiveUserTask(long, Map)` | Find and complete the active user task |
| `Optional<Long> findActiveUserTaskKey(long)` | Find it, or empty |
| `void cancel(long, String reason)` | Cancel an instance with a reason visible in Operate |

Two implementation details matter operationally:

**Bounded exponential backoff on user-task search** (`CamundaProcessService.java:180-212`). Camunda's search index is eventually consistent and can lag task activation by hundreds of milliseconds. Naive code finds nothing and fails intermittently. The framework retries up to **5 attempts** starting at **200 ms**, doubling each time (200 / 400 / 800 / 1600 ms, ~3 s total), and returns `Optional.empty()` rather than throwing. `completeActiveUserTask` converts the empty result into a `ProcessServiceException`. It also handles interruption correctly — restoring the interrupt flag and returning empty rather than swallowing it (`:201-205`).

**Multi-tenant handling** (`:78-131`). When `framework.camunda.multi-tenant=false` (the default) and a command nonetheless carries a `tenantId`, the value is **silently dropped** and a DEBUG line is logged. Enable DEBUG on `com.aaseya` if tenant routing appears to be ignored. Note also that `publish()` does not forward `tenantId` at all — the underlying `PublishMessageCommand` has no such field in 8.9, and the code documents this with a precise TODO explaining what to add when Camunda exposes it (`:161-167`).

`publish()` is also the only method that wraps failures in `ProcessServiceException`; `start`, `correlate`, and `cancel` let the client's own exception propagate.

### 8.5 Anti-corruption layer — `VariableMapper`

Sits between Camunda's untyped variable JSON and your clean Java records, centralising all Jackson configuration so BPMN wire names appear in one place rather than scattered across worker classes.

`createDefault()` establishes the baseline: `FAIL_ON_UNKNOWN_PROPERTIES=false` (a BPMN process may carry variables your worker does not care about — that must not be an error), `JavaTimeModule` registered, and `NON_NULL` serialization inclusion (`VariableMapper.java:39-45`).

The distinctive piece is `validateRequiredComponents` (`:111-136`). After binding, it reflects over the target record's components and asserts each is non-null, **unless** annotated with anything whose simple name is `Nullable`. A violation throws `VariableBindingException` with a message that names the culprit and tells you where to look:

> `Required variable 'orderId' is null in ReserveInventoryVars — check that the BPMN process supplies this variable.`

That is the difference between a five-minute fix and an afternoon of debugging a `NullPointerException` three call-frames deep.

---

## 9. Architecture guardrails (ArchUnit)

`framework-test/.../ArchitectureRules.java` exposes six ready-to-use `ArchRule` constants. They are ordinary JUnit tests — a violation is a **red build**, not a review comment.

| # | Rule constant | Enforces |
|---|---|---|
| 1 | `WEB_AND_WORKERS_MUST_NOT_ACCESS_INFRASTRUCTURE` | `..web..` / `..workers..` must not touch `..infrastructure..` or `..repository..` |
| 2 | `ONLY_INFRASTRUCTURE_CAMUNDA_MAY_IMPORT_CAMUNDA_CLIENT` | Only `..infrastructure.camunda..` and `com.aaseya.camunda.framework..` may depend on `io.camunda.client..` |
| 3 | `DOMAIN_MUST_NOT_IMPORT_SPRING_WEB_OR_CAMUNDA` | `..domain..` must not depend on `org.springframework.web..`, `jakarta.servlet..`, or `io.camunda..` |
| 4 | `REST_CONTROLLERS_MUST_NOT_BE_TRANSACTIONAL` | No `@Transactional` on `@RestController` classes |
| 5 | `CONTROLLER_METHODS_MUST_NOT_EXPOSE_ENTITIES` | Controller method params and return types must not be JPA `@Entity` types |
| 6 | `USE_CONSTRUCTOR_INJECTION` | No `@Autowired` on fields |

### 9.1 Why each rule exists

The reasoning is documented in the source Javadoc and is worth reading in full; summarised:

**1 — Layering.** The application layer must stay ignorant of persistence and messaging infrastructure. Bypassing the boundary produces brittle tests and code that cannot be swapped for a different infrastructure implementation.

**2 — Camunda boundary.** Three concrete benefits: the client never leaks into domain logic; switching transport (REST↔gRPC) becomes a pure infrastructure change; and tests can mock `ProcessService` without a live cluster.

**3 — Domain purity.** Enables plain unit tests with no Spring context, lets the domain be reused across transports, and prevents the anemic-domain-model anti-pattern where domain objects degrade into annotation-decorated data bags.

**4 — No `@Transactional` controllers.** Transactions held open during HTTP I/O tie up connections longer than necessary; it invites business logic into the controller; and rollback semantics become unpredictable when Spring MVC's exception-handler chain swallows an exception before the transaction manager sees it.

**5 — No entities at the HTTP boundary.** Leaks persistence metadata into the API contract, risks lazy-loading exceptions when Jackson serialises an uninitialised proxy after the session closes, and makes versioning hard because a schema change forces an API change. Rule 5 is the only one implemented as a custom `ArchCondition`, inspecting return type and every parameter for a `jakarta.persistence.Entity` annotation (`ArchitectureRules.java:173-206`).

**6 — Constructor injection.** Field injection makes a class impossible to instantiate in a plain unit test, hides dependencies from the constructor signature, and prevents `final` fields. `@Autowired` *on a constructor* is still permitted — the rule targets fields only.

### 9.2 Wiring them into a service — and the catch

These rules **do not run automatically**. A consuming service must declare an ArchUnit test class:

```java
@AnalyzeClasses(packages = "com.myorg.myservice")
class MyServiceArchitectureTest {
    @ArchTest static final ArchRule layering =
        ArchitectureRules.WEB_AND_WORKERS_MUST_NOT_ACCESS_INFRASTRUCTURE;
    @ArchTest static final ArchRule camundaBoundary =
        ArchitectureRules.ONLY_INFRASTRUCTURE_CAMUNDA_MAY_IMPORT_CAMUNDA_CLIENT;
    @ArchTest static final ArchRule domainIsolation =
        ArchitectureRules.DOMAIN_MUST_NOT_IMPORT_SPRING_WEB_OR_CAMUNDA;
    @ArchTest static final ArchRule txOnControllers =
        ArchitectureRules.REST_CONTROLLERS_MUST_NOT_BE_TRANSACTIONAL;
    @ArchTest static final ArchRule entityExposure =
        ArchitectureRules.CONTROLLER_METHODS_MUST_NOT_EXPOSE_ENTITIES;
    @ArchTest static final ArchRule constructorInjection =
        ArchitectureRules.USE_CONSTRUCTOR_INJECTION;
}
```

**A team that forgets this file gets zero enforcement and no warning.** Add it as step one of scaffolding, not as an afterthought. Note also that the rules assume a specific package layout — `web`, `workers`, `domain`, `infrastructure.camunda`, `repository`. Name your packages differently and the rules silently match nothing.

---
---

# Part C — Operating it

## 10. Configuration reference

### 10.1 `framework.camunda.*`

```yaml
framework:
  camunda:
    multi-tenant: false          # forward tenantId to the engine; false silently drops it
    worker:
      max-jobs-active: 32        # max simultaneously-activated jobs per worker
      poll-interval: PT30S       # poll interval when in-flight < max-jobs-active
      retry-backoff: PT5S        # base retry back-off hint for technical failures
      default-retries: 3         # retry budget for jobs with no explicit BPMN retry count
```

Defined in `FrameworkCamundaProperties`. The Javadoc carries useful operational guidance: raising `max-jobs-active` improves throughput on fast jobs but raises memory pressure proportionally to payload size; and **the Kubernetes `preStop` sleep must be at least as long as `poll-interval`** to allow graceful drain on pod shutdown (`FrameworkCamundaProperties.java:97-104`). The chart's default `preStop.sleepSeconds: 35` is consistent with the default `PT30S`.

### 10.2 `framework.security.*`

```yaml
framework:
  security:
    cors:
      enabled: false             # opt-in; the bean is not created when false
      allowed-origins: []
      allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
      allowed-headers: ["*"]
      allow-credentials: false
      max-age: PT1H
    jwt:
      roles-claim: realm_access.roles   # dot-path into the JWT claims tree
      role-prefix: ROLE_
```

### 10.3 `framework.observability.*`

```yaml
framework:
  observability:
    mdc:
      enabled: true
      header-name: X-Correlation-Id
      generate-if-absent: true
      tenant-id-header-name: X-Tenant-Id
    metrics:
      business-counter-prefix: ""   # your domain name; used when you declare FrameworkCounters
```

### 10.4 `framework.data.*`

```yaml
framework:
  data:
    audit:
      enabled: true
      created-by-header: X-User-Id
    flyway:
      enforce-naming-convention: true
      expected-locations: [classpath:db/migration]
```

Two honesty notes here (see §15.5): `audit.enabled` and `audit.created-by-header` are **declared but not consumed** — `AuditColumnListener` hardcodes `X-User-Id` and is registered unconditionally. `flyway.expected-locations` is explicitly documented as informational metadata only; Flyway itself is configured via `spring.flyway.locations` (`FrameworkDataProperties.java:163-169`).

### 10.5 `framework.web.*` and `framework.outbox.*`

```yaml
framework:
  web:
    exception-handler-enabled: true    # false disables GlobalExceptionHandler
  outbox:
    poll-interval: PT2S                # read by @Scheduled on JdbcOutboxRelay.poll()
```

`framework.outbox.poll-interval` has no `@ConfigurationProperties` class — it is referenced only as a SpEL default inside the `@Scheduled` annotation (`JdbcOutboxRelay.java:121`), so it will not appear in IDE property completion.

### 10.6 The scaffold's environment surface

`service-template/src/main/resources/application.yml` is entirely env-var driven:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/service_template` | JDBC URL |
| `DB_USER` | `service_template` | DB user |
| `DB_PASSWORD` | *(empty)* | DB password |
| `CAMUNDA_CLIENT_ID` | *(empty)* | OAuth client ID |
| `CAMUNDA_CLIENT_SECRET` | *(empty)* | OAuth client secret |
| `CAMUNDA_CLUSTER_ID` | *(empty)* | SaaS cluster ID |
| `CAMUNDA_REGION` | *(empty)* | SaaS region, e.g. `bru-2` — **required and case-sensitive** |

Fixed in the same file: `camunda.client.mode: saas`, `ddl-auto: validate`, `open-in-view: false`, `flyway.enabled: true`, `server.port: 8080`, actuator exposing `health,info,prometheus` with probes enabled.

### 10.7 The five profiles

| Profile | What it changes |
|---|---|
| `local` | Datasource → H2 in-memory `MODE=PostgreSQL;DB_CLOSE_DELAY=-1`, `H2Dialect`. No Postgres needed |
| `dev` | `root: INFO`, `com.aaseya: DEBUG`, `spring.security: DEBUG`; actuator `include: "*"`; SQL echo + formatting on |
| `qa` / `uat` | Intermediate verbosity/exposure between dev and prod |
| `prod` | `root: WARN`, `com.aaseya: INFO`; actuator limited to `health,info,prometheus`; SQL echo off |

The profiles differ **only** in log verbosity and actuator exposure — they do not change wiring.

---

## 11. Build, test and quality gates

### 11.1 The canonical command

```bash
mvn verify        # from the repository root — the full gate
```

> ⚠️ **`mvn verify` requires a running Docker daemon.** One test in `framework-test` starts a Camunda container via Testcontainers. Without Docker the build fails at that module and the two after it are skipped. See §11.2.

Useful narrower forms:

```bash
mvn -q -pl framework-core test                    # one module (-am to build its deps too)
mvn -pl framework-core test -Dtest=BaseWorkerTest # one class
mvn -pl framework-core test -Dtest=BaseWorkerTest#methodName
```

> ⚠️ The `local` profile does **not** start as shipped — see §15.9 for the four defects and their fixes. The instructions below assume those fixes are applied.

**Option A — self-managed Camunda in Docker (no cloud account).** Camunda 8.9 can run as a single container backed by H2 instead of Elasticsearch, which is how the `camunda-process-test` harness does it:

```bash
docker run -d --name camunda-local -p 26500:26500 -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=broker,consolidated-auth,security \
  -e CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=rdbms \
  -e CAMUNDA_DATABASE_TYPE=rdbms \
  -e 'CAMUNDA_DATABASE_URL=jdbc:h2:mem:camunda;DB_CLOSE_DELAY=-1;MODE=PostgreSQL' \
  -e CAMUNDA_DATABASE_USERNAME=sa -e CAMUNDA_DATABASE_PASSWORD= \
  -e ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME=io.camunda.exporter.rdbms.RdbmsExporter \
  -e CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
  -e CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
  camunda/camunda:8.9.0

curl -s http://localhost:8080/v2/topology     # wait for "health":"healthy"
```

Point the service at it with a profile that overrides `camunda.client.mode` (the SaaS default comes from `CamundaSaasDefaultsEnvironmentPostProcessor`) and moves the service off port 8080, which the engine occupies:

```yaml
# application-localdocker.yml
server: { port: 8081 }
camunda:
  client:
    mode: self-managed
    rest-address: http://localhost:8080
    grpc-address: http://localhost:26500
    auth: { method: none }
```

```bash
mvn spring-boot:run -pl service-template -Dspring-boot.run.profiles=localdocker
```

**Option B — Camunda SaaS.** Run the scaffold against a cloud cluster with H2 locally, after loading the `CAMUNDA_*` variables:

```bash
# Bash / Git Bash / WSL
set -a && source .env && set +a
mvn spring-boot:run -pl service-template -Dspring-boot.run.profiles=local
```

```powershell
# PowerShell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2])
    }
}
mvn spring-boot:run -pl service-template "-Dspring-boot.run.profiles=local"
```

Expected on startup: `/actuator/health` returns `{"status":"UP"}`, the log shows a successful cluster connection, and Flyway creates both framework tables in H2. **No jobs are pulled** — the scaffold registers no workers. That is correct behaviour, not a fault.

### 11.2 Test inventory — and the Docker prerequisite

**With a Docker daemon running, `mvn verify` passes exactly as the README describes** — "`BUILD SUCCESS` across eight modules with 183 tests passing (0 failures)" (`README.md:70`). Verified against this snapshot:

```
[INFO] camunda-process-framework .......................... SUCCESS [  0.003 s]
[INFO] framework-core ..................................... SUCCESS [ 10.985 s]
[INFO] framework-camunda-starter .......................... SUCCESS [  5.564 s]
[INFO] framework-security-starter ......................... SUCCESS [  2.771 s]
[INFO] framework-observability-starter .................... SUCCESS [  4.308 s]
[INFO] framework-data-starter ............................. SUCCESS [  4.950 s]
[INFO] framework-test ..................................... SUCCESS [01:54 min]
[INFO] framework-web-starter .............................. SUCCESS [  5.590 s]
[INFO] service-template ................................... SUCCESS [  2.867 s]
[INFO] BUILD SUCCESS
```

**Verified test counts — 183 total, 0 failures:**

| Module | Tests | Test classes |
|---|---|---|
| `framework-core` | 54 | `BaseWorkerTest`, `VariableMapperTest`, `CamundaProcessServiceTest`, `JdbcIdempotencyGuardTest`, `AuditableEntityTest`, `FrameworkExceptionTest` |
| `framework-data-starter` | 49 | auto-config beans, properties, env post-processor, audit listener, Flyway validator |
| `framework-test` | 26 | architecture rules, JDBC factory, MDC assertions, Camunda scenario base |
| `framework-security-starter` | 15 | auto-config beans, properties, JWT converter |
| `framework-web-starter` | 14 | auto-config, exception handler, response |
| `framework-camunda-starter` | 13 | auto-config beans, properties, env post-processor |
| `framework-observability-starter` | 12 | auto-config beans, properties, counters, MDC filter |
| `service-template` | 0 | *(scaffold — no tests by design)* |
| **Total** | **183** | **28 classes** |

#### Docker is a hard prerequisite, and the README does not say so

`framework-test`'s `CamundaScenarioTestBaseTest` is annotated `@CamundaSpringProcessTest`, which starts a Testcontainers-managed `camunda/camunda:8.9.0` container. That single test class accounts for **107 of the ~114 seconds** the module takes.

Without a Docker daemon the same command fails:

```
org.testcontainers.containers.ContainerFetchException:
  Can't get Docker image: RemoteDockerImage(imageName=camunda/camunda:8.9.0, ...)
Caused by: java.lang.IllegalStateException:
  Could not find a valid Docker environment.
```

`framework-test` then fails, the reactor halts, and `framework-web-starter` and `service-template` never build — 168 of the 183 tests run, 167 pass, 1 errors. Yet the README's prerequisites list marks Docker as "Optional: … for the deploy artifacts" (`README.md:60`). **It is not optional for `mvn verify`.**

If Docker is unavailable, either of these works:

```bash
mvn install -DskipTests                                    # build only, all 8 modules
mvn verify -Dtest='!CamundaScenarioTestBaseTest' -DfailIfNoSpecifiedTests=false
```

### 11.3 The coverage gate — documented but not wired

**This is important and contradicts three other documents in the repo.**

`README.md`, `CLAUDE.md`, and `.gitlab-ci.yml:136-140` all describe a JaCoCo gate: 80% line coverage overall, 90% on `framework-core` domain packages. The `.gitlab-ci.yml` comment states "The plugin is configured in pom.xml with two rules".

It is not. The parent POM declares JaCoCo only inside `<pluginManagement>` (`pom.xml:108-161`), which supplies configuration to modules that declare the plugin but **does not add it to any build**. The parent's own comment says so explicitly (`pom.xml:121-123`):

> The 'check' goal (0.80 line coverage) is intentionally NOT bound on the parent aggregator (no code here); child modules that produce code bind it in their own `<build>` section.

But no child module has a `<build><plugins>` section declaring JaCoCo. Only two POMs in the repo contain a `<build>` element at all — the parent, and `service-template`, whose build section contains solely the `spring-boot-maven-plugin`.

**Consequences:**
- JaCoCo's `prepare-agent` never runs → no instrumentation.
- `report` never runs → no `target/site/jacoco/` output, so the CI job's `coverage_report` artifact path matches nothing.
- `check` never runs → **the 80%/90% thresholds are not enforced by anything.**

**Verified empirically.** After a full **green** `mvn verify` (`BUILD SUCCESS`, 183/183 tests passing) against this snapshot, a filesystem search for `jacoco*` and `*.exec` across the entire repository returns **zero files** — no `jacoco.exec`, no `target/site/jacoco/`, nothing. Likewise no `failsafe-reports` directory exists in any module. The README's claim that the build produces "JaCoCo coverage reports under each module's `target/site/jacoco/`" (`README.md:74`) does not hold.

The same reasoning applies to **Failsafe**. Both README and CLAUDE.md describe `mvn verify` as running "integration/scenario tests (Failsafe)". Failsafe is not bound by the default Maven lifecycle — it requires an explicit plugin declaration with `integration-test` and `verify` goals. It appears only in the parent's `<pluginManagement>`, so **no `*IT.java` test would execute**. (Surefire *is* bound by the default lifecycle, so unit tests do run.)

None of this makes the framework unsound — the tests exist and, Docker aside, pass. But the quality gate the documentation advertises is currently inactive. See §15.10 for the fix.

### 11.4 Is it running? — verification and triage

The scaffold gives an unusually weak *impression* of running, because it registers no workers and pulls no jobs: a healthy instance and a broken one both sit there doing nothing visible. Use these checks rather than the absence of errors.

Port conventions used below: **8080** the Camunda engine, **8081** the service under `mvn spring-boot:run`, **8082** the service in a container. Adjust to your own mapping.

#### The one check that matters

```bash
curl -s http://localhost:8081/actuator/health
```

| Response | Meaning |
|---|---|
| `{"groups":["liveness","readiness"],"status":"UP"}` | Running correctly |
| `{"status":"DOWN", ...}` | Started, but a health contributor is failing — usually the Camunda client |
| nothing, `curl` exit code **7** | Not running at all (connection refused) |
| nothing, `curl` exit code **28** | Started but not yet accepting traffic — retry for ~30 s before concluding it is stuck |

The two probes can be queried separately, which distinguishes "alive" from "ready to serve":

```bash
curl -s http://localhost:8081/actuator/health/liveness    # {"status":"UP"}
curl -s http://localhost:8081/actuator/health/readiness   # {"status":"UP"}
```

#### Full checklist

**1 — The process exists.**

```bash
# container
docker ps --filter name=svc-tmpl --format "{{.Names}}: {{.Status}}"
#   svc-tmpl: Up 4 minutes (healthy)

# maven run — check the port instead
netstat -ano | findstr :8081        # PowerShell / cmd
lsof -i :8081                       # Linux / macOS
```

`docker ps` without `-a` lists only *running* containers. If yours is absent, re-run with `-a` — a container that exited still appears there, with its exit code.

**2 — Docker's own verdict** (containers only). The image ships a `HEALTHCHECK` polling `/actuator/health/liveness` every 30 s with a 60 s start-period:

```bash
docker inspect --format='{{.State.Health.Status}}' svc-tmpl
#   healthy
```

`starting` is normal for the first ~45 s. `unhealthy` after that means the JVM is up but liveness is failing.

**3 — Startup actually completed.** Spring logs exactly one line on success:

```bash
docker logs svc-tmpl 2>&1 | grep "Started Application"
#   Started Application in 18.595 seconds (process running for 19.672)
```

Absence of this line means the context never finished building — scroll up for the first `Caused by:`.

**4 — Flyway created the framework tables.** Without these, `IdempotencyGuard` and `OutboxRelay` fail at first use (§15.9 ②):

```bash
docker logs svc-tmpl 2>&1 | grep -E "Successfully applied|already up to date"
#   Successfully applied 1 migration to schema "PUBLIC", now at version v1
```

On a persistent database you can check directly:

```bash
docker exec -it svc-tmpl-pg psql -U service_template -d service_template -c '\dt'
#   flyway_schema_history | worker_execution | process_outbox
```

**5 — The engine is reachable.** Confirm the engine itself, then that the service can see it:

```bash
curl -s http://localhost:8080/v2/topology | grep -o '"health":"[a-z]*"'
#   "health":"healthy"

docker logs svc-tmpl 2>&1 | grep -icE "UNAUTHENTICATED|UNAVAILABLE|Connection refused|well known"
#   0
```

**6 — What you should NOT expect.** No job-activation logs. The scaffold ships no `@JobWorker`, so silence at this level is correct and is not evidence of a broken cluster connection. Activity appears only once you add a worker.

#### Triage

| Symptom | Cause | Fix |
|---|---|---|
| `curl` exit 7, nothing on the port | Never started, or exited | `docker ps -a` / check the terminal; read the first `Caused by:` |
| Container `Exited (1)` seconds after start | Context init failed — most often Camunda credentials | See below |
| `Exited (1)`, log ends at `Cannot load driver class: org.h2.Driver` | H2 at `test` scope | §15.9 ① |
| Starts, but no `Successfully applied` line | Flyway auto-config missing | §15.9 ② |
| `Port 8080 is already in use` | Engine already holds it | `-Dserver.port=8081`, or `-e SERVER_PORT=…` + `-p` |
| Health `DOWN`, `camunda` contributor failing | Wrong cluster/region, or expired credentials | Re-check the four `CAMUNDA_*` values |
| `unhealthy` past ~60 s | Liveness failing inside the container | `docker exec svc-tmpl curl -f http://localhost:8080/actuator/health/liveness` |

**On credentials specifically** — and contrary to `DEPLOYMENT-LOCAL.md`, which says they "fail late at the first Camunda call" (§15.3) — bad or missing credentials kill the process during context initialisation, exit code 1:

```
Error creating bean with name 'camundaClientCredentialsProvider' ...
  Failed to retrieve well known configuration
Caused by: java.net.ConnectException: Connection refused
```

That is good news for triage: the failure is immediate and loud, so a container that stays `Up` has already proved it authenticated successfully.

---

## 12. The CI/CD pipeline

`.gitlab-ci.yml` — a reference pipeline, well-commented, with an operator setup checklist in its header.

### 12.1 Stages and jobs

The declared stage order (`.gitlab-ci.yml:49-58`) is:

```
build → test → quality-gate → sonar → image → image-scan → helm-lint → deploy-dev → smoke
```

> Note: the root README (`README.md:225`) lists these in a slightly different order and calls the third stage `coverage-gate`. The YAML is authoritative — `quality-gate` is a stage containing two jobs, one of which is named `coverage-gate`.

| Job | Stage | What it does |
|---|---|---|
| `compile` | build | `mvn compile`; archives `**/target/*.jar` for a week |
| `unit-test` | test | `mvn verify -pl <7 modules> -am`; publishes JUnit + JaCoCo reports |
| `coverage-gate` | quality-gate | Re-runs `verify` with `-Djacoco.skip=false -Dmaven.test.skip=true` (see §11.3 — currently a no-op) |
| `bpmn-integrity` | quality-gate | Runs `tools/check-bpmn-integrity.sh` |
| `sonar-scan` | sonar | `mvn verify sonar:sonar` with `sonar.qualitygate.wait=true` — the pipeline blocks on the server-side gate (0 blockers, 0 critical vulns, <3% duplication) |
| `docker-build` | image | BuildKit build of `service-template/Dockerfile`, pushes `$CI_REGISTRY_IMAGE/service-template:$CI_COMMIT_SHORT_SHA`; on a tag push, also pushes the tag name |
| `trivy-scan` | image-scan | `trivy image --exit-code 1 --severity HIGH,CRITICAL --ignore-unfixed` |
| `helm-lint` | helm-lint | `helm lint`, then `helm template … | kubeval --strict` **if kubeval is available**, otherwise skips with a message |
| `deploy-dev` | deploy-dev | **Manual trigger.** `helm upgrade --install --wait --timeout 5m` |
| `smoke-dev` | smoke | Polls `/actuator/health` for `"status":"UP"`, 10 attempts × 10 s |

**Shared config via `extends:`** — three anchors keep the file DRY: `.maven-job` (Maven image + `.m2` cache keyed on `pom.xml`), `.rules-standard` (merge requests + pushes to `main`), and `.rules-release` (`main` + `v*` tags). Jobs from `image` onward use `.rules-release`, so container images are only built on `main` and tags — not on every MR.

**Required CI/CD variables** (documented in the file header, `:5-25`): `SONAR_HOST_URL`, `SONAR_TOKEN` (masked), `KUBE_NAMESPACE`, `KUBE_CONFIG` (base64 kubeconfig, dev-scoped); `CI_REGISTRY*` are GitLab-provided. The header also flags one out-of-scope follow-up: `sonar-project.properties` does not exist in the repo and must be created for the Sonar scan to be properly configured.

### 12.2 The distinctive part — `check-bpmn-integrity.sh`

This is the framework's most original quality idea, and it addresses a failure mode most teams never think to test for. It runs against every `*.bpmn` file in the repo and performs four checks.

**Check 1 — XML well-formedness.** `xmllint --noout` per file. If `xmllint` is not installed it emits a WARN and skips; warnings do not affect the exit code.

**Check 2 — Task definition ↔ `@JobWorker` cross-reference, bidirectional.** It builds two maps — every `<zeebe:taskDefinition type="X">` in every BPMN, and every `@JobWorker(type = "X")` in every `src/main/java/**/*.java` — then reports **both** kinds of orphan (`:132-145`):

- a type in BPMN with no matching worker → *"Orphan service task type 'X' defined in BPMN but no `@JobWorker(type="X")` found"* — the process would hang forever at that step;
- a worker with no matching BPMN type → *"Orphan `@JobWorker(type="X")` has no corresponding `<zeebe:taskDefinition>`"* — dead code, or a typo in one of the two strings.

Either is a `FAIL` and exit code 1.

**Check 3 — BPMN DI completeness.** Every flow node (service tasks, user tasks, all gateway and event variants) must have a matching `<bpmndi:BPMNShape>`, and every `<bpmn:sequenceFlow>` a matching `<bpmndi:BPMNEdge>`. This catches diagrams that are semantically valid but render as invisible or disconnected in Camunda Modeler and Operate — which matters, because Operate is the operator's primary window into a stuck process.

**Check 4 — Candidate-group inventory.** Extracts every `candidateGroups` value from `<zeebe:assignmentDefinition>` (splitting comma-separated lists), deduplicates, and prints them for manual identity-provider verification. This is informational, not a failure — a human must confirm each group exists in Keycloak. It closes the loop on cardinal invariant 1: *each human step is a user task assigned to a persona candidate group equal to an IdP group name.*

**On this repo today**, with no BPMN files present, the script exits 0 immediately with `"No BPMN files found; nothing to validate."` (`:62-65`). It becomes meaningful the moment a consuming team adds diagrams. Requires Bash with `mapfile` and GNU `grep -P` (Git Bash, WSL, or Linux CI).

---

## 13. Deployment

### 13.1 The container image

`service-template/Dockerfile` — two stages, and every line earns its place.

**Stage 1 (builder, `maven:3.9-eclipse-temurin-21`):**
1. Copy **only the POM files** first, then run `mvn dependency:go-offline`. Docker caches this layer independently of source changes, so an ordinary code edit does not re-download the dependency tree.
2. Copy sources, `mvn package -DskipTests -pl service-template -am`.
3. Run `java -Djarmode=layertools -jar … extract` to split the fat jar into four layers ordered by volatility: `dependencies` → `spring-boot-loader` → `snapshot-dependencies` → `application`.

**Stage 2 (runtime, `eclipse-temurin:21-jre`):**
- Installs `curl` for the healthcheck and purges the apt cache **in the same layer**.
- Creates a non-root `spring` user/group at UID/GID 1000; runs `USER 1000`.
- Copies the four extracted layers in ascending-volatility order, so a code-only change invalidates only the final `application` layer.
- `JAVA_OPTS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75"` — exit immediately on OOM rather than thrashing, and size the heap from the container limit rather than a hardcoded `-Xmx` that drifts out of sync with it.
- `HEALTHCHECK` on `/actuator/health/liveness` every 30 s with a 60 s start period.
- Entrypoint invokes Boot 4's launcher class directly: `org.springframework.boot.loader.launch.JarLauncher`, via `sh -c exec` so `$JAVA_OPTS` is expanded at runtime.

> ⚠️ **As shipped, this Dockerfile does not build** — two independent failures (missing `framework-web-starter` in the COPY list; `groupadd` colliding with the base image's UID 1000), plus a third that stops the resulting container from starting on the `local` profile (H2 at `test` scope). Reproductions and fixes in §15.9 ⑤⑥ and ①.

Build from the **repository root** (the Dockerfile expects that context):

```bash
docker build -f service-template/Dockerfile -t <registry>/service-template:<tag> .
```

With those fixes applied the image builds and runs. Verified end to end against a local self-managed engine:

```bash
docker run -d --name svc-tmpl \
  -e SPRING_PROFILES_ACTIVE=local \
  -e CAMUNDA_CLIENT_MODE=self-managed \
  -e CAMUNDA_CLIENT_AUTH_METHOD=none \
  -e CAMUNDA_CLIENT_RESTADDRESS=http://host.docker.internal:8080 \
  -e CAMUNDA_CLIENT_GRPCADDRESS=http://host.docker.internal:26500 \
  -p 8082:8080 service-template:local
```

Result: image 657 MB; container runs as `uid=1000(spring) gid=1000(spring)` — matching the chart's `runAsUser`/`fsGroup`; Flyway applies V1; `Started Application in 18.6 seconds`; `/actuator/health` returns `{"status":"UP"}`; and the built-in `HEALTHCHECK` reports `healthy` about 45 s in, comfortably inside its 60 s `--start-period`.

Note `CAMUNDA_CLIENT_AUTH_METHOD=none`: the `local` profile inherits `camunda.client.mode=saas` from `application.yml`, so pointing the image at a self-managed engine means overriding both the mode *and* the auth method. Omit the latter and the client still attempts an OAuth discovery call and the container exits 1 (§15.3).

### 13.2 The Helm chart

`deploy/helm-chart/` — a generic chart plus per-service `values-<service>.yaml` overlays. Nothing is hardcoded: no namespace, no registry, no secret. Requires Helm 3.10+ and Kubernetes 1.25+ (`autoscaling/v2`, `policy/v1`).

| Template | What it provides |
|---|---|
| `deployment.yaml` | 2 replicas by default; `runAsNonRoot`, `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`; requests 250m/512Mi, limits 1000m/1Gi |
| Probes | Liveness (`/actuator/health/liveness`), readiness (`/actuator/health/readiness`), and a **startup probe** with `failureThreshold: 30 × 5 s` = up to 150 s for a slow JVM start |
| `hpa.yaml` | 2–10 replicas; 70% CPU / 80% memory targets; optional custom Pods-type metrics |
| `pdb.yaml` | `minAvailable: 1` during voluntary disruptions |
| `networkpolicy.yaml` | Egress-only allowlist by port |
| `servicemonitor.yaml` | Prometheus Operator scrape of `/actuator/prometheus` every 30 s |
| `configmap.yaml` | Key/value pairs injected via `envFrom` |
| `serviceaccount.yaml` | Optional annotations for IRSA/workload identity |

Graceful shutdown is handled deliberately: `terminationGracePeriodSeconds: 60` with a `preStop` sleep of 35 s to drain load-balancer connections — and, per §10.1, that sleep must be **at least** the worker `poll-interval` (default 30 s) so in-flight job polling can wind down.

Secrets are injected via `envFrom.secretRef`, expected to be managed by external-secrets or Sealed Secrets. The chart README documents the two secrets a service needs (`<release>-camunda-secrets`, `<release>-db-secrets`) and gives literal `kubectl create secret` examples for bootstrapping.

```bash
helm install service-template deploy/helm-chart \
  -f deploy/helm-chart/values-service-template.yaml \
  -n camunda-services --create-namespace
```

`helm uninstall` does not remove PVCs or Secrets — clean those up manually.

---
---

# Part D — Using and extending

## 14. Adopting it: the scaffold workflow

### 14.1 The eight steps

1. **Copy `service-template/`** into your target repository, renaming it (e.g. `booking-service/`).
2. **Rename the base package** `com.aaseya.camunda.service.template` → `com.<yourorg>.<yourservice>` across all sources **and** in the `pom.xml` `<mainClass>` configuration (`service-template/pom.xml:109`).
3. **Wire the dependency.** In the same monorepo, add the module to the parent `<modules>` list. In a separate repository, consume the artifacts externally:
   ```xml
   <dependency>
     <groupId>com.aaseya.camunda</groupId>
     <artifactId>framework-camunda-starter</artifactId>
     <version>0.1.0-SNAPSHOT</version>
   </dependency>
   ```
   That starter pulls `framework-core` transitively. Add `framework-security-starter`, `framework-observability-starter`, `framework-data-starter`, `framework-web-starter` as needed, and `framework-test` at `<scope>test</scope>`.
4. **Add the ArchUnit test class** (§9.2). Do this now — it costs two minutes and is the thing that keeps everything else true.
5. **Drop BPMN files** under `src/main/resources/processes/`.
6. **Write workers** — one per automated BPMN step, extending `BaseWorker<V>`, under `infrastructure/camunda/`.
7. **Write domain aggregates** under `domain/`, extending `AuditableEntity<StatusEnum>` where a validated state machine helps.
8. **Write REST controllers** under `web/` returning DTOs/records — never entities. Add Flyway migrations from `V2__` onward.

### 14.2 Package layout — get this right first

The ArchUnit rules match on package names. This layout is what they assume:

```
com.<yourorg>.<yourservice>
├── web/                      REST controllers. DTOs only. No @Transactional.
├── workers/                  (optional) worker-adjacent code
├── domain/                   Pure business logic. No Spring Web, Servlet, or Camunda imports.
├── application/              Use-case services. Transactions belong here.
├── infrastructure/
│   ├── camunda/              The ONLY package allowed to import io.camunda.client
│   └── ...
└── repository/               Spring Data repositories
```

**One inconsistency to resolve up front.** The root README (`README.md:126-127`) says put BPMN under `src/main/resources/processes/` and workers under `<yourpkg>.workers/`; `service-template/README.md:26-27` says workers under `infrastructure/camunda/` and BPMN under `src/main/resources/`.

**Recommendation: put workers under `infrastructure/camunda/`.** A worker extending `BaseWorker` necessarily imports `io.camunda.client.api.response.ActivatedJob` and `io.camunda.client.api.worker.JobClient`, and ArchUnit rule 2 permits those imports **only** inside `..infrastructure.camunda..`. Placing workers under `workers/` fails the build. For BPMN, `src/main/resources/processes/` is the better convention — it is a subdirectory of what the other doc says, so both statements remain true.

### 14.3 Local setup

Copy `.env.example` → `.env` and fill in the four Camunda values. Note the example file lists **only** the `CAMUNDA_*` variables — if you run against Postgres rather than the `local` H2 profile you also need `DB_URL`, `DB_USER`, and `DB_PASSWORD`, which `application.yml` reads but `.env.example` never mentions. Load syntax is shell-specific; see §11.1.

### 14.4 The five cardinal invariants

Every service on this framework is expected to hold these true:

1. **BPMN is the source of truth for every lifecycle.** Each automated step maps 1:1 to a `@JobWorker`; each human step is a user task whose candidate group equals an IdP group name.
2. **Workers and controllers are inbound adapters.** No business rules — those live in `domain/`.
3. **Orchestration-based saga.** Services never call each other directly for saga steps; Camunda mediates every hop. Rollback is a BPMN compensation boundary event plus a `<step>-compensate` worker, never hand-coded undo logic.
4. **Domain code never imports `io.camunda.client.*`.** All engine access goes through `ProcessService`.
5. **Business failure ≠ technical failure.** (§7)

---

## 15. Known gaps, gotchas and roadmap

Stated plainly rather than buried. Items 7 and 3 are the ones that will actually bite you.

### 15.1 The Jackson 2.x / 3.x split

Spring Boot 4 auto-configures a Jackson **3.x** `JsonMapper` (`tools.jackson.databind`). The framework and Camunda 8.9 both use Jackson **2.x** (`com.fasterxml.jackson`). The camunda starter bridges this by providing a 2.x `ObjectMapper` via `@ConditionalOnMissingBean`.

**If your service defines its own `ObjectMapper` bean, ours backs off** — and if yours is the 3.x type, `VariableMapper` and `JdbcOutboxRelay` will fail to wire. Symptom: `NoSuchBeanDefinitionException: ObjectMapper`, or a type-mismatch at startup. Ensure any mapper you declare is `com.fasterxml.jackson.databind.ObjectMapper`.

### 15.2 Version label mismatch

The distribution directory is named `0.1.1`; every POM says `0.1.0-SNAPSHOT`; the READMEs say `0.1.0-SNAPSHOT`. **Treat the POM as authoritative** for the Maven coordinate.

### 15.3 `DEPLOYMENT-LOCAL.md` — present, but none of its three paths run as written

`DEPLOYMENT-LOCAL.md` is referenced by `README.md` and `service-template/README.md:42`. It was absent from the original snapshot and has since been supplied. It is a thorough, well-organised guide: three run paths (A = H2 via Maven, B = Postgres in Docker, C = the container image), IntelliJ and Eclipse run-configuration walkthroughs, a verification checklist, and troubleshooting.

**Its instructions are sound; the code underneath them is not.** Every path was executed against this snapshot. All three fail, each for defects in the repo rather than errors in the guide:

| Claim | Outcome |
|---|---|
| Step 2 — "183 tests passing, 0 failures" | ✅ Exactly right (Docker required — §15.8) |
| Step 2 — "JaCoCo coverage reports under each module's `target/site/jacoco/`" | ❌ Zero files produced (§15.10) |
| Prerequisites — Docker required "for Paths B and C" | ❌ Also required by Step 2, which every path depends on |
| **Path A** — "Fastest path — nothing to install" | ❌ `Cannot load driver class: org.h2.Driver` (§15.9 ①) |
| Path A verify — "log shows `Successfully applied 1 migration`" | ❌ Flyway auto-config absent, then migration fails on H2 (§15.9 ②③) |
| **Path B** — "Flyway will create `worker_execution` and `process_outbox`" | ❌ Same missing auto-config; the partial index *would* work on Postgres |
| **Path C** — `docker build -f service-template/Dockerfile .` | ❌ Two build failures (§15.9 ⑤⑥) |
| IDE — missing creds "fail late at the first Camunda call, not at startup" | ❌ Inverted — see below |
| References — `PROGRESS.md` | ❌ Not present in the repo |

**On the credentials claim.** The guide states in two places that absent `CAMUNDA_*` values "fail late at the first Camunda call, not at bind time", warning that a run configuration missing them "can *look* fine until it isn't". The opposite is true, and it is worth correcting because it changes how you debug: with no credentials the container exits during context initialisation with **exit code 1**, before Tomcat finishes starting:

```
Error creating bean with name 'camundaClientCredentialsProvider' ...
  Failed to instantiate [io.camunda.client.CredentialsProvider]:
  Factory method 'camundaClientCredentialsProvider' threw exception with message:
  Failed to retrieve well known configuration
Caused by: java.net.ConnectException: Connection refused
```

The `camundaClient` bean is a hard startup dependency of `camundaHealthCheck`, so a misconfigured cluster is a loud, immediate failure — never a silent one. Verified by running the image with no `CAMUNDA_*` variables at all.

Once the §15.9 fixes are applied, Paths A and C both work.

### 15.4 Worker/BPMN location inconsistency

Covered in §14.2. Two documents disagree; ArchUnit rule 2 settles it in favour of `infrastructure/camunda/`.

### 15.5 Declared-but-unwired properties

- `framework.data.audit.created-by-header` — `FrameworkDataProperties.Audit` declares it with a default of `X-User-Id` and Javadoc describing exactly how the listener uses it, but `AuditColumnListener` hardcodes its own `DEFAULT_USER_HEADER = "X-User-Id"` constant and never receives the properties object (`AuditColumnListener.java:59`, `:205-215`). **Changing the property has no effect.**
- `framework.data.audit.enabled` — the `AuditColumnListener` bean is registered with no `@ConditionalOnProperty` guard, so setting this to `false` does not disable anything. (In practice the listener only acts on entities that opt in via `@EntityListeners`, so the blast radius is small.)
- `framework.data.flyway.expected-locations` — explicitly documented as informational only.

### 15.6 Deliberate omissions that surprise people

Neither of these is a bug — both are documented design decisions — but both catch newcomers:

- **No `SecurityFilterChain` bean.** The security starter gives you a `JwtAuthenticationConverter` and a `CorsConfigurationSource`; you must write the filter chain and inject them. The class Javadoc shows a minimal example (`FrameworkSecurityAutoConfiguration.java:36-49`), including the wrinkle that when CORS is disabled the `CorsConfigurationSource` bean does not exist, so the parameter must be omitted or made optional.
- **No `FrameworkCounters` bean.** The domain prefix is application-specific, so you declare it yourself (`FrameworkObservabilityAutoConfiguration.java:32-40`).

### 15.7 `framework-web-starter` is missing from the build plumbing

The module is declared in the parent POM (`pom.xml:41`), builds fine, and has its own tests — but it is referenced **nowhere** in the Docker or CI plumbing:

- **`service-template/Dockerfile`** copies seven module directories and omits `framework-web-starter` entirely — neither its `pom.xml` nor its `src`. Because the parent POM declares it as a reactor module, Maven inside the container fails to resolve the reactor. **The Docker build is currently broken**, and the `docker-build` CI job with it. Fix: add the two `COPY` lines alongside the others.
- **`.gitlab-ci.yml`** passes an explicit seven-module `-pl` list to both `unit-test` and `coverage-gate` (`:117`, `:147`) that omits `framework-web-starter`. Since `service-template` does not depend on it either, `-am` will not pull it in. **Its three test classes never run in CI.** Fix: add it to both lists, or drop `-pl` and let the whole reactor build.

Both look like straightforward oversights from when `framework-web-starter` was added after the rest of the plumbing was written. The Dockerfile half is **confirmed fatal, not theoretical** — `docker build` aborts on the very first Maven step with `Child module /workspace/framework-web-starter of /workspace/pom.xml does not exist` (reproduction and fix in §15.9 ⑤).

### 15.8 Docker is an undocumented prerequisite for `mvn verify`

The README lists Docker as "Optional: … for the deploy artifacts" (`README.md:60`) and presents `mvn verify` as producing `BUILD SUCCESS`. In fact `framework-test`'s `CamundaScenarioTestBaseTest` starts a Testcontainers-managed `camunda/camunda:8.9.0` container, so **without a Docker daemon `mvn verify` fails** and the two modules after `framework-test` in the reactor never build. Verified — see §11.2 for the exact output.

Fixes, in rough order of preference: tag the test so it is skipped when no Docker environment is detected (Testcontainers' `DockerClientFactory.instance().isDockerAvailable()` inside a JUnit `@EnabledIf`); or move it to a Failsafe-run `*IT` class once Failsafe is actually wired (§15.10); or at minimum promote Docker to a required prerequisite in the README.

### 15.9 The scaffold does not start as documented — six defects

Both `README.md` and `DEPLOYMENT-LOCAL.md` say: run with the `local` profile and "Flyway creates `worker_execution` and `process_outbox` in H2"; and build the container with `docker build -f service-template/Dockerfile .`. Attempting exactly that surfaces six separate defects, in the order you hit them — four on the Maven path (①–④) and two more on the Docker path (⑤–⑥). All six were reproduced and fixed against this snapshot.

**① H2 is `test`-scoped, so the `local` profile cannot start.**
`service-template/pom.xml` declares H2 with `<scope>test</scope>`, but `application-local.yml` points the datasource at H2. `mvn spring-boot:run` uses the *runtime* classpath, so:

```
Caused by: java.lang.IllegalStateException: Cannot load driver class: org.h2.Driver
```

*Fix:* change the H2 dependency to `<scope>runtime</scope>`. *Workaround without editing the POM:* add `-Dspring-boot.run.useTestClasspath=true`.

**② Flyway auto-configuration is missing entirely, so no migration ever runs.**
Spring Boot 4 split auto-configuration out of `spring-boot-autoconfigure` into per-technology modules. The scaffold depends on `flyway-core` and `flyway-database-postgresql`, but **not** on `org.springframework.boot:spring-boot-flyway`. Inspecting the fat jar confirms it: `spring-boot-jdbc`, `spring-boot-jpa`, `spring-boot-data-jpa`, `spring-boot-validation` and friends are all bundled — `spring-boot-flyway-4.0.5.jar` is absent, and `spring-boot-autoconfigure` contains zero Flyway classes.

The consequence is silent: `spring.flyway.enabled: true` is inert, no migration runs, no error is logged, and **`worker_execution` and `process_outbox` are never created**. The framework's two headline reliability patterns then fail at first use — `IdempotencyGuard.check()` and `OutboxRelay.poll()` both query tables that do not exist. Because `poll()` swallows and logs its exceptions (§8.2), an outbox running against a missing table degrades to an ERROR line every two seconds rather than a startup failure.

*Fix:* add the missing module to `service-template/pom.xml`:
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-flyway</artifactId>
</dependency>
```

**③ `V1__framework_tables.sql` fails on H2.**
With Flyway actually running, the shipped migration aborts on its last statement:

```
Syntax error in SQL statement "CREATE INDEX idx_process_outbox_undispatched
  ON process_outbox (created_at) [*]WHERE dispatched_at IS NULL"; [42000-240]
Line: 23
```

Partial indexes (`CREATE INDEX … WHERE`) are PostgreSQL-only; H2 rejects them even under `MODE=PostgreSQL`. This is a near-miss in the original: the file's header comment explains that `payload` is `TEXT` rather than `jsonb` *specifically* for H2 compatibility, so cross-database portability was considered — the partial index was simply overlooked.

*Fix:* make the index portable and leave the Postgres optimisation to a later migration:
```sql
CREATE INDEX idx_process_outbox_undispatched
    ON process_outbox (dispatched_at, created_at);
```

**④ `/actuator/prometheus` returns 404.**
`application.yml` exposes `health,info,prometheus`, and the Helm chart's `ServiceMonitor` scrapes `/actuator/prometheus` every 30 s by default. But `service-template` depends only on `framework-camunda-starter` and `framework-core` — not on `framework-observability-starter` — so no Prometheus meter registry exists and the endpoint 404s. A service deployed straight from the scaffold gets a ServiceMonitor pointed at a missing endpoint.

*Fix:* add `framework-observability-starter` to the scaffold's dependencies, or drop `prometheus` from the exposure list until a service opts in.

**⑤ The Docker build fails on the reactor.** `service-template/Dockerfile` copies seven of the eight module directories, omitting `framework-web-starter` — which the parent POM declares as a reactor module (§15.7). `mvn dependency:go-offline` therefore aborts on the first build step:

```
[ERROR] Child module /workspace/framework-web-starter of /workspace/pom.xml does not exist
ERROR: failed to solve: process "/bin/sh -c mvn -B -q dependency:go-offline"
       did not complete successfully: exit code: 1
```

*Fix:* add the two missing `COPY` lines, alongside the other seven modules:
```dockerfile
COPY framework-web-starter/pom.xml  ./framework-web-starter/pom.xml
COPY framework-web-starter/src      ./framework-web-starter/src
```

**⑥ The runtime stage fails on a UID collision.** The `eclipse-temurin:21-jre` base is Ubuntu 24.04, which already ships an `ubuntu` account at UID/GID 1000:

```
groupadd: GID '1000' already exists
ERROR: process "/bin/sh -c groupadd --gid 1000 spring && useradd --uid 1000 ..."
       did not complete successfully: exit code: 4
```

UID 1000 cannot simply be changed — `deploy/helm-chart/values.yaml:33-36` pins `runAsUser: 1000` and `fsGroup: 1000`, so the image and the chart must agree. Free the UID instead:

```dockerfile
RUN (userdel -r ubuntu 2>/dev/null || true) \
    && groupadd --gid 1000 spring \
    && useradd --uid 1000 --gid spring --shell /bin/bash --create-home spring
```

Note that defect ① compounds here: `DEPLOYMENT-LOCAL.md` runs Path C with `SPRING_PROFILES_ACTIVE=local`, which needs H2 *inside the image*. At `test` scope it is absent from the fat jar, and unlike Path A there is no `-Dspring-boot.run.useTestClasspath=true` escape hatch in a container. Path C therefore requires the scope change, not the workaround.

**After all six fixes**, the documented flow works. Verified:

```
o.f.core.internal.command.DbMigrate : Migrating schema "PUBLIC" to version "1 - framework tables"
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema "PUBLIC", now at version v1
o.s.boot.tomcat.TomcatWebServer     : Tomcat started on port 8081 (http) with context path '/'
c.a.c.service.template.Application  : Started Application in 7.842 seconds
```
with `/actuator/health` returning `{"status":"UP"}`.

### 15.10 The coverage and integration-test gates are inactive

Covered in detail in §11.3. In short: JaCoCo appears only in the parent's `<pluginManagement>` and is declared in no module's `<build>`, so `prepare-agent`, `report`, and `check` never execute — the advertised 80%/90% thresholds enforce nothing, and the CI `coverage_report` artifact path matches no file. Failsafe is in the same position, so `*IT.java` integration tests would not run.

**Fix:** add a `<build><plugins>` block declaring `jacoco-maven-plugin` (and `maven-failsafe-plugin`) to each code-bearing module — the executions are already configured in the parent's `pluginManagement`, so the child declaration can be version-less and configuration-less. Then add the `check` execution with the two rules the documentation describes.

### 15.11 Smaller notes

- **`BaseWorker.extractBusinessKey()` constructs `new ObjectMapper()` on every invocation** (`BaseWorker.java:285`) rather than reusing the injected `VariableMapper`. That is a fresh Jackson mapper — a comparatively expensive object — allocated per job. Harmless at low volume; worth hoisting to a static field for high-throughput workers.
- **`sonar-project.properties` does not exist** — flagged as a follow-up in the CI file's own header (`:22-24`). The Sonar scan will run with defaults until it is added.
- **`kubeval` is not in the `alpine/helm` image**, so the `helm-lint` job's schema validation silently skips with a printed notice. Extend the image or add an install step to actually get it.
- **No dead-letter path in the outbox.** A permanently un-dispatchable row is retried every poll interval forever, with an ERROR log each time. Consider an attempt counter if you rely on it heavily.

### 15.12 Roadmap

The README's own "Extending the framework" section names **Phase 4 (Adoption)** as the next planned work:

- A **cookbook** of recipes for common patterns — a saga step, a user-task step, a compensation path, a decision-routed gateway, starting a process from a domain state change.
- A **scaffolder script or Maven archetype**, replacing the manual copy-and-rename in §14.1.

Contribution rules: changes to `framework-core` must be paired with a unit test for new behaviour, and with an ArchUnit test if they introduce a new rule shape. Version bumps to Spring Boot, Camunda, Postgres, or Java require explicit maintainer approval.

---
---

# Appendix A — File inventory

### `framework-core` (24 main + 6 test classes)
```
audit/       AuditableEntity
exception/   FrameworkException, BusinessException, TechnicalException,
             RetryableException, NonRetryableException,
             IllegalStateTransitionException, BusinessError, package-info
idempotency/ IdempotencyGuard, JdbcIdempotencyGuard
mdc/         MdcKeys
outbox/      OutboxRelay, JdbcOutboxRelay, OutboxEntry
process/     ProcessService, CamundaProcessService, ProcessServiceException,
             StartProcessCommand, CorrelateMessageCommand, PublishMessageCommand
worker/      BaseWorker, WorkResult, VariableMapper, VariableBindingException
tests/       BaseWorkerTest, VariableMapperTest, CamundaProcessServiceTest,
             JdbcIdempotencyGuardTest, AuditableEntityTest, FrameworkExceptionTest
```

### The five starters
```
framework-camunda-starter/        FrameworkCamundaAutoConfiguration,
                                  FrameworkCamundaProperties,
                                  CamundaSaasDefaultsEnvironmentPostProcessor
                                  + AutoConfiguration.imports + EnvironmentPostProcessor.imports
framework-security-starter/       FrameworkSecurityAutoConfiguration,
                                  FrameworkSecurityProperties,
                                  JwtRealmRolesAuthenticationConverter
                                  + AutoConfiguration.imports
framework-observability-starter/  FrameworkObservabilityAutoConfiguration,
                                  FrameworkObservabilityProperties,
                                  MdcCorrelationFilter, FrameworkCounters
                                  + AutoConfiguration.imports
framework-data-starter/           FrameworkDataAutoConfiguration,
                                  FrameworkDataProperties,
                                  FrameworkDataDefaultsEnvironmentPostProcessor,
                                  AuditColumnListener, FlywayNamingConventionValidator
                                  + AutoConfiguration.imports + EnvironmentPostProcessor.imports
framework-web-starter/            FrameworkWebAutoConfiguration, FrameworkWebProperties,
                                  GlobalExceptionHandler, Response
                                  + AutoConfiguration.imports
```

### `framework-test` and `service-template`
```
framework-test/   archunit/ArchitectureRules, process/CamundaScenarioTestBase,
                  db/JdbcTemplateTestFactory, mdc/MdcAssertions
service-template/ Application, application.yml + 5 profile overlays,
                  db/migration/V1__framework_tables.sql, Dockerfile, README, pom.xml
```

### Repository root
```
pom.xml                          parent POM — modules, pinned versions, pluginManagement
README.md                        operator/consumer reference
UNDERSTANDING.md                 orientation study guide
FRAMEWORK-GUIDE.md               this document
CLAUDE.md                        AI-agent cheat-sheet
.gitlab-ci.yml                   nine-stage reference pipeline
.env.example                     credential template (CAMUNDA_* only)
tools/check-bpmn-integrity.sh    BPMN reference-integrity checker
deploy/helm-chart/               Chart.yaml, values.yaml, values-service-template.yaml,
                                 README.md, templates/ (deployment, service, hpa, pdb,
                                 networkpolicy, servicemonitor, configmap, serviceaccount,
                                 _helpers.tpl)
```

---

# Appendix B — Framework DDL

`service-template/src/main/resources/db/migration/V1__framework_tables.sql`. Consumer migrations begin at `V2__`.

```sql
CREATE TABLE worker_execution (
    business_key   VARCHAR(200) NOT NULL,
    element_id     VARCHAR(200) NOT NULL,
    completed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result_hash    VARCHAR(200),
    PRIMARY KEY (business_key, element_id)
);

CREATE TABLE process_outbox (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(200) NOT NULL,
    kind           VARCHAR(20)  NOT NULL,   -- 'START' | 'MESSAGE'
    payload        TEXT         NOT NULL,   -- TEXT, not jsonb, for H2 compatibility
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at  TIMESTAMP
);

CREATE INDEX idx_process_outbox_undispatched
    ON process_outbox (created_at)
    WHERE dispatched_at IS NULL;
```

Optional Postgres follow-up if you need native JSON operators:

```sql
ALTER TABLE process_outbox ALTER COLUMN payload TYPE jsonb USING payload::jsonb;
```

---

# Appendix C — Glossary

| Term | Meaning in this project |
|---|---|
| **BPMN** | Business Process Model and Notation — the XML flowchart defining a process. **The source of truth**; every step maps to code |
| **Process instance** | One running execution of a BPMN definition (one order, one claim). Identified by a `processInstanceKey` |
| **Job** | A unit of work the engine hands to a worker for an automated step |
| **Worker** | The Java class that performs a job. Here, extends `BaseWorker<V>` and carries `@JobWorker(type = "...")` |
| **Job type** | The string linking a BPMN `<zeebe:taskDefinition type="X">` to a `@JobWorker(type = "X")`. Must match exactly |
| **User task** | A human step. Assigned to a *candidate group* that maps to an identity-provider group |
| **Candidate group** | The IdP group name eligible to claim a user task |
| **Process variables** | The JSON data payload carried through a process instance. Bound to Java records by `VariableMapper` |
| **Business key** | A caller-supplied domain identifier (order ID, booking ID) that drives idempotency and log correlation |
| **Correlation** | Routing a message to the right waiting process instance, keyed on a correlation key |
| **Message** | An external signal that resumes a waiting instance (`correlate`) or starts a new one (`publish`) |
| **Saga** | A multi-service business transaction. Here **orchestration-based** — Camunda mediates every hop |
| **Compensation** | Undoing a completed saga step. Modelled as a BPMN compensation boundary event plus a `<step>-compensate` worker |
| **Incident** | Camunda's term for a job that exhausted its retries and needs human intervention. Visible in Operate |
| **Retry budget** | The remaining retry count on a job. Technical failures decrement it; business errors do not |
| **Idempotency** | Guaranteeing that re-executing the same job produces no additional side effects |
| **Transactional outbox** | Writing an intended external call to a table in the same transaction as the domain change, then dispatching asynchronously |
| **MDC** | SLF4J Mapped Diagnostic Context — thread-local key/value pairs auto-attached to every log line |
| **Anti-corruption layer** | A translation boundary keeping an external system's data model out of your domain. Here, `VariableMapper` |
| **Starter** | A Spring Boot JAR that auto-configures a capability by being on the classpath |
| **ArchUnit** | A Java library for asserting architectural rules as unit tests |
| **RFC 7807 / ProblemDetail** | The standard HTTP error-response format (`type`, `title`, `status`, `detail`, plus extensions) |

---

*Generated from source at `camunda-process-framework 0.1.1` (`0.1.0-SNAPSHOT`). Every claim carries a `file:line` reference — verify against the code rather than trusting this document where the two disagree.*
