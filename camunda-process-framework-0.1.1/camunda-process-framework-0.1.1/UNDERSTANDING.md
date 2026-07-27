# Understanding this base framework — assessment study guide

> Your lead handed you `camunda-process-framework` and asked you to understand its design,
> pipeline, modules, and design patterns before starting the assessment work next week.
> This document is that walkthrough. Read it top to bottom once, then keep it open while you
> click through the code.

---

## 1. What is this, in one paragraph?

It is a **reusable base framework** (a library, plus a copy-me scaffold) for building
**microservices that orchestrate business processes with Camunda 8.9**. Think of Camunda as a
"workflow engine": you draw a business process as a flowchart (a **BPMN** diagram), and the engine
walks each process instance through the steps, calling your code for automated steps and pausing at
human steps. This framework gives every team a **consistent, hardened starting point** so they don't
re-solve the same problems (error handling, idempotency, security, metrics, DB conventions) on every
new service. It does **not** contain any actual business process — it's the foundation you build on.

Analogy: it's like a company-standard "starter kit" for a house. It gives you the foundation,
plumbing, and wiring standards. Each team then builds their own rooms (BPMN processes, workers,
domain logic) on top.

**Key facts (from `pom.xml` / `README.md`):**
- Java 21, Spring Boot 4.0.5, Camunda 8.9.0 SaaS (cloud), PostgreSQL 16 (H2 for local).
- Maven multi-module project, group `com.aaseya.camunda`.
- Versions are deliberately **pinned** — they must move together, don't upgrade casually.

---

## 2. Core vocabulary (learn these five terms first)

| Term | Meaning in this project |
|---|---|
| **BPMN** | The XML flowchart that defines a business process. **It is the source of truth** — every step in the diagram maps to code. |
| **Job / Worker** | An automated step in the BPMN is a "job". A **worker** is the Java class that does that step's work. Here, workers extend `BaseWorker<V>`. |
| **User task** | A human step in the BPMN. Assigned to a "candidate group" that maps to an identity-provider (Keycloak) group. |
| **Process variables** | The data payload carried through a process instance. Deserialized into Java records via `VariableMapper`. |
| **Saga / orchestration** | A multi-service business transaction. Here Camunda *mediates every hop* — services never call each other directly. Rollback = BPMN "compensation" events, not hand-written undo code. |

---

## 3. The modules — what each one does and why it exists

This is a Maven **multi-module** build. The root `pom.xml` is a "parent POM" that lists 8 modules
and pins all dependency versions in one place. Seven are the framework; the eighth is the scaffold.

| Module | One-line role | The interesting thing inside |
|---|---|---|
| **framework-core** | Pure patterns library. No Spring magic. | `BaseWorker`, `ProcessService`, `VariableMapper`, `IdempotencyGuard`, `OutboxRelay`, `AuditableEntity`, the exception hierarchy. **Start reading here.** |
| **framework-camunda-starter** | Spring Boot "auto-configuration" — turns the core patterns into ready-to-inject beans. | Registers `ProcessService`, `VariableMapper`, etc. as beans automatically. Sets `camunda.client.mode=saas` by default. |
| **framework-security-starter** | Login/authorization layer (OAuth2 + JWT). | `JwtRealmRolesAuthenticationConverter` turns a JWT claim (Keycloak `realm_access.roles`) into Spring `ROLE_*` permissions. Optional CORS. |
| **framework-observability-starter** | Metrics, tracing, log correlation. | `MdcCorrelationFilter` stamps an `X-Correlation-Id` on every request/log so you can trace one request across services. `FrameworkCounters` for business metrics. |
| **framework-data-starter** | Database conventions (JPA + Flyway). | `AuditColumnListener` auto-fills `createdAt/updatedAt/createdBy/updatedBy`. `FlywayNamingConventionValidator` enforces migration file naming. |
| **framework-web-starter** | REST API conventions. | `Response<T>` standard envelope + `GlobalExceptionHandler` that turns framework exceptions into standard HTTP error responses (RFC 7807 "ProblemDetail"): business error → 422, retryable → 503, etc. |
| **framework-test** | Shared test tooling. | Six **ArchUnit** rules (see §6) + `CamundaScenarioTestBase` for process tests. Consumers depend on this at `test` scope. |
| **service-template** | The runnable scaffold you **copy** to start a new service. | Wires the starters together, has `application.yml` + profile overlays (local/dev/qa/uat/prod), and `V1__framework_tables.sql`. **Contains no business code on purpose.** |

**Why split into starters?** A team building a pure backend worker service can pull in
`framework-camunda-starter` only; a team also exposing REST APIs adds `framework-web-starter`; and so
on. You take what you need. Each starter self-registers via Spring's auto-configuration mechanism
(the files under `src/main/resources/META-INF/spring/`).

---

## 4. How a request actually flows (the mental model)

**Automated step (the most important flow):**

```
Camunda engine has a job ready
        │
        ▼
Your worker's @JobWorker method delegates to BaseWorker.execute()
        │
        ├─ 1. Bind process variables → typed Java record (VariableMapper)
        ├─ 2. Push MDC context (correlation id, process key…) for logging
        ├─ 3. Idempotency check → if this job already ran, complete silently
        ├─ 4. validate() hook
        ├─ 5. doWork(vars, job)  ← THE ONLY PART YOU WRITE
        ├─ 6. Inspect the returned WorkResult:
        │        Completed     → tell Camunda "done" + output variables
        │        BusinessError → tell Camunda "throw BPMN error <code>"
        │        Compensated   → complete a rollback step
        ├─ 7. Emit a Micrometer metric (framework_job_*_total)
        └─ 8. Clear MDC (finally)
```

You only write step 5. Everything else is framework plumbing you inherit. Read
`framework-core/.../worker/BaseWorker.java` — it's ~290 lines and is the single best file to
understand the whole design philosophy.

**The one rule to remember — business failure vs technical failure:**
- **Business failure** ("customer has insufficient funds") → return a `BusinessError` → Camunda
  routes it via a BPMN boundary event to an alternate path. This is a *normal, expected* outcome.
- **Technical failure** ("database timed out") → throw an exception → Camunda retries, and if retries
  run out it raises an *incident* for an operator. This is an *abnormal* outcome.

Getting this distinction right is the heart of the framework.

---

## 5. The design patterns in use (name-drop these in your assessment)

| Pattern | Where | Why it's here |
|---|---|---|
| **Template Method** | `BaseWorker<V>` — `final execute()` fixes the algorithm, subclass fills in `doWork()`/`varsType()`. | Guarantees every worker handles errors, idempotency, MDC, and metrics identically. |
| **Facade** | `ProcessService` wraps the raw `CamundaClient`. | Business code never talks to Camunda's API directly — one seam to mock in tests and to swap transport. |
| **Anti-Corruption Layer** | `VariableMapper` (Jackson 2.x) between Camunda's untyped variables and your clean Java records. | Isolates the domain from the engine's data format. |
| **Idempotency / Dedup key** | `IdempotencyGuard` + `worker_execution` table (composite PK). | Camunda may deliver a job more than once; this makes re-delivery safe. |
| **Transactional Outbox** | `OutboxRelay` + `process_outbox` table, polled with `FOR UPDATE SKIP LOCKED`. | Reliably publishes messages to Camunda in the same DB transaction as the domain change — no lost/double messages. |
| **State Machine** | `AuditableEntity<S extends Enum<S>>` with `allowedTransitions()`. | Illegal status transitions throw instead of silently corrupting state. |
| **Sealed result type** | `WorkResult` = `Completed | BusinessError | Compensated`. | Forces every worker outcome to be one of a known, exhaustive set. |
| **Orchestration Saga + Compensation** | Enforced by convention + BPMN. | Distributed transactions without a 2-phase commit; rollback is modeled in the diagram, not coded by hand. |
| **Auto-configuration starters** | The five `*-starter` modules. | Spring Boot's idiomatic "opt-in capability" packaging. |
| **Layered / Hexagonal architecture** | Enforced by ArchUnit (§6). | Keeps domain logic pure and infrastructure swappable. |

---

## 6. Architecture guardrails (ArchUnit rules)

`framework-test/.../ArchitectureRules.java` defines **six rules** that a consuming service runs as
unit tests. They *fail the build* if the layering is violated. This is how the framework enforces its
own design instead of relying on code review:

1. Web controllers & workers must not reach into `infrastructure`/`repository` directly.
2. Only `infrastructure.camunda` code may import `io.camunda.client` — everyone else uses `ProcessService`.
3. `domain` code must not import Spring Web, Servlet, or Camunda — pure business logic only.
4. `@RestController` must not be `@Transactional`.
5. Controller methods must not expose JPA `@Entity` types — use DTOs.
6. No field injection — constructor injection only.

When someone asks "how does this framework stay clean over time?", the answer is: these rules.

---

## 7. The pipeline (build + CI/CD)

**Local build:** `mvn verify` from the root. That compiles all 8 modules, runs unit tests
(Surefire), integration/scenario tests (Failsafe), and enforces **JaCoCo code-coverage gates**
(80% overall, 90% on core domain). README claims 183 tests passing.

**CI/CD:** `.gitlab-ci.yml` — a GitLab pipeline with roughly these stages:

```
build → test → coverage-gate → sonar → bpmn-integrity → image →
image-scan (Trivy) → helm-lint → deploy-dev (manual) → smoke-dev
```

Notable custom stage: **bpmn-integrity** runs `tools/check-bpmn-integrity.sh`, which cross-checks
that every `<zeebe:taskDefinition type="X">` in a BPMN file has a matching `@JobWorker(type="X")` in
Java (and vice-versa), that the diagram is visually complete, and inventories the human-task
candidate groups for identity-provider verification. This catches "you drew a step but forgot to code
it" before deploy.

**Deploy artifacts:**
- `service-template/Dockerfile` — multi-stage build, runs as non-root user, has a health check.
- `deploy/helm-chart/` — a generic Kubernetes Helm chart with per-service value overlays, autoscaling
  (HPA), pod disruption budgets, network policy, and Prometheus `ServiceMonitor`.

---

## 8. How you would actually *use* it (the scaffold workflow)

From the root README's "Scaffolding a new service" section — this is likely close to what the
assessment will ask you to do:

1. Copy `service-template/` to a new module/repo and rename the package
   `com.aaseya.camunda.service.template` → your service's package.
2. Add the framework starters you need as dependencies (`framework-camunda-starter` is the base;
   it transitively pulls in `framework-core`).
3. Drop your **BPMN** files under `src/main/resources/processes/`.
4. Write **workers** extending `BaseWorker<V>` (one per automated BPMN step).
5. Write **domain** aggregates extending `AuditableEntity<Status>` for validated state machines.
6. Write **REST controllers** returning DTOs (never entities).
7. Add **Flyway migrations** from `V2__…` onward (V1 is the framework's own).

To run the scaffold today: copy `.env.example` → `.env`, fill the four `CAMUNDA_*` values, load them
into your shell, then `mvn spring-boot:run -pl service-template -Dspring-boot.run.profiles=local`
(uses in-memory H2, no Postgres needed). It connects but does nothing, because there are no workers
yet — that's expected.

---

## 9. A suggested reading order for the code

1. `README.md` (root) — the authoritative narrative, config reference, and troubleshooting.
2. `framework-core/.../worker/BaseWorker.java` — the design philosophy in one file.
3. `framework-core/.../worker/WorkResult.java` and the `exception/` package — the outcome model.
4. `framework-core/.../process/ProcessService.java` + `CamundaProcessService.java` — the engine facade.
5. `framework-core/.../idempotency/` and `.../outbox/` — the reliability patterns.
6. `framework-test/.../ArchitectureRules.java` — the guardrails.
7. `service-template/src/main/resources/application.yml` + `db/migration/V1__framework_tables.sql` — how it's wired and what tables exist.
8. `.gitlab-ci.yml` + `tools/check-bpmn-integrity.sh` — the pipeline.
9. `CLAUDE.md` (in this repo) — a condensed engineering cheat-sheet with the gotchas.

---

## 10. Gotchas worth knowing before the assessment

- **Jackson version split:** the framework uses Jackson **2.x**; Spring Boot 4 defaults to Jackson
  **3.x**. The framework supplies a 2.x `ObjectMapper` bean; don't accidentally override it with a 3.x one.
- **`@EnableScheduling` is required** on your app class or the outbox poller silently never runs.
- **Worker not picking up jobs?** The BPMN `type="..."` must exactly match `@JobWorker(type="...")`.
- **Version label mismatch:** the folder says `0.1.1`, the poms say `0.1.0-SNAPSHOT`. Trust the pom.
- **`DEPLOYMENT-LOCAL.md` is referenced but missing** from this zip — its content lives in the root README.
- **The scaffold is intentionally empty** — no sample process. Don't go looking for "the demo"; there
  isn't one, and that's a deliberate design decision.

---

### TL;DR for your lead

> It's a Spring Boot 4 / Java 21 multi-module **base framework for Camunda 8.9 process
> orchestration**. `framework-core` holds the reusable patterns (a Template-Method `BaseWorker`, a
> `ProcessService` facade over the Camunda client, idempotency guard, transactional outbox, auditable
> state-machine base); five Spring-Boot **starters** package those as opt-in auto-configured
> capabilities (camunda, security, observability, data, web); `framework-test` enforces the layered
> architecture with ArchUnit rules; and `service-template` is the empty runnable scaffold teams copy
> to start a new service. A GitLab pipeline builds, tests with coverage gates, validates BPMN↔worker
> integrity, scans the image, and deploys via a Helm chart. The central design rule is the strict
> separation of **business failures** (routed by BPMN) from **technical failures** (retried into
> incidents).
