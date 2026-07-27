# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Multi-module Maven framework (`com.aaseya.camunda:camunda-process-framework`) for building
process-orchestrated microservices on **Camunda 8.9 SaaS**. It is a *library + scaffold*, not
a running application: seven reusable modules (patterns library + five Boot starters + a test
library) plus `service-template`, a bare runnable scaffold that consuming teams copy to start a
new service. There is **no sample BPMN, worker, or domain code** anywhere in the repo — that is
intentional and must stay that way (see `service-template/README.md`).

Stack is version-pinned and the compatibility matrix is fragile: **Java 21**, **Spring Boot
4.0.5** (the exact version Camunda 8.9.0 was built against), **Camunda 8.9.0** (SaaS, REST
protocol), Jackson **2.x**. Version bumps to any of these require explicit maintainer approval —
do not upgrade them casually.

## Commands

Run everything from the repository root.

```bash
mvn verify                    # full build + all tests (the canonical check) — REQUIRES DOCKER
mvn install -DskipTests       # build only, ~40s, no Docker needed
mvn -q -pl framework-core test        # tests for a single module (-am to also build its deps)
mvn -pl framework-core test -Dtest=BaseWorkerTest             # single test class
mvn -pl framework-core test -Dtest=BaseWorkerTest#methodName  # single test method

# Run the scaffold locally (H2 in-memory, no Postgres needed). Requires CAMUNDA_* env vars loaded first.
mvn spring-boot:run -pl service-template -Dspring-boot.run.profiles=local

# Run against a self-managed Camunda in Docker instead of SaaS — no credentials needed.
# Service listens on 8081 (the engine takes 8080). See README "Without a Camunda Cloud account".
mvn spring-boot:run -pl service-template -Dspring-boot.run.profiles=localdocker

# BPMN reference-integrity checks (Git Bash / WSL; needs xmllint for XML validation)
./tools/check-bpmn-integrity.sh
```

`mvn verify` runs unit tests via Surefire — **183 tests across 8 modules, verified green**. Two
caveats that contradict the other docs, both confirmed empirically:

- **Docker is mandatory**, not optional. `framework-test`'s `CamundaScenarioTestBaseTest` starts a
  `camunda/camunda:8.9.0` Testcontainer (~107s of the run). Without a daemon the build fails there
  and `framework-web-starter` + `service-template` are skipped. Escape hatch:
  `-Dtest='!CamundaScenarioTestBaseTest' -DfailIfNoSpecifiedTests=false`.
- **The JaCoCo gate and Failsafe do not run at all.** Both appear only in the parent's
  `<pluginManagement>`; no module declares them in a `<build>`, so there is no `jacoco.exec`, no
  `target/site/jacoco/`, no coverage enforcement, and `*IT.java` tests never execute. The 80%/90%
  thresholds described in README/CI are currently fiction.

CI (`.gitlab-ci.yml`) runs these same steps plus Sonar, `check-bpmn-integrity.sh`, Trivy image scan,
and helm-lint. Note its `-pl` lists omit `framework-web-starter`, so that module's tests never run in CI.

## Startup defects — fixed in this working copy

The scaffold did not start as documented. Six defects were reproduced and fixed; if you are working
from a fresh copy of the original, expect to re-apply these (full write-up in `FRAMEWORK-GUIDE.md` §15.9).
A **seventh** was found later (2026-07-27) when the first *consuming* service actually injected the
framework's beans — see below the table.

| # | Defect | Fix |
|---|---|---|
| 1 | H2 at `<scope>test</scope>` → `Cannot load driver class: org.h2.Driver` on the `local` profile | changed to `runtime` in `service-template/pom.xml` |
| 2 | Boot 4 splits auto-config per technology; `spring-boot-flyway` was absent, so **Flyway never ran** and the two framework tables were never created — silently | added `org.springframework.boot:spring-boot-flyway` |
| 3 | `V1__framework_tables.sql` used a partial index (`CREATE INDEX … WHERE`), which is Postgres-only and fails on H2 | index is now `(dispatched_at, created_at)` |
| 4 | `/actuator/prometheus` 404s — scaffold doesn't depend on `framework-observability-starter`, yet advertises the endpoint and the Helm ServiceMonitor scrapes it | not fixed; dependency decision |
| 5 | `Dockerfile` omits `framework-web-starter` → `docker build` dies on `Child module … does not exist` | added both `COPY` lines |
| 6 | `groupadd --gid 1000` collides with the Ubuntu-24.04 base image's `ubuntu` account | `userdel -r ubuntu` first; UID 1000 must be kept (Helm pins `runAsUser: 1000`) |
| 7 | **Auto-config ordering**: `FrameworkCamundaAutoConfiguration`'s beans use `@ConditionalOnBean(CamundaClient/JdbcTemplate)` but the class had no ordering hint, so it was evaluated (alphabetically, `com.aaseya…`) *before* the Camunda SDK / Spring JDBC registered those beans → `ProcessService` and `IdempotencyGuard` were **silently skipped**. | added `@AutoConfiguration(afterName = { "io.camunda.client.spring.configuration.CamundaAutoConfiguration", "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration" })` in `framework-camunda-starter` |

Defect 2 is the dangerous one: no error, no log line, and both headline reliability patterns
(`IdempotencyGuard`, `OutboxRelay`) then query tables that do not exist.

**Defect 7 — why it hid for so long, and why it matters.** `service-template` never injects
`ProcessService` or `IdempotencyGuard` (it has no domain/worker code), so the silently-skipped beans
were never noticed here. The sibling project `order-service-sample` is the first code that actually
injects them, so it exposed the ordering bug (`No qualifying bean of type 'ProcessService'` at
startup). The fix lives in
`framework-camunda-starter/.../FrameworkCamundaAutoConfiguration.java` and must be re-applied +
`mvn install`-ed if the framework is restored from a fresh copy. Recommendation: add a test (or extend
`service-template`) that injects `ProcessService`/`IdempotencyGuard` so this cannot regress. Full
narrative in the sibling `order-service-sample/CLAUDE.md` and `..\ASSESSMENT-WALKTHROUGH.md`.

**Contrary to `DEPLOYMENT-LOCAL.md`**, missing/invalid `CAMUNDA_*` credentials do *not* "fail late at
the first Camunda call" — the `camundaClient` bean is a hard dependency of `camundaHealthCheck`, so
the context fails to initialise and the process exits 1 immediately.

To run the scaffold, copy `.env.example` → `.env`, fill in `CAMUNDA_CLIENT_ID`,
`CAMUNDA_CLIENT_SECRET`, `CAMUNDA_CLUSTER_ID`, `CAMUNDA_REGION`, and load them into the process
environment (loader syntax is shell-specific — see the root README run section for PowerShell vs
Bash). The scaffold connects to Camunda but registers no workers, so it pulls no jobs — that is
expected.

## Module dependency shape

```
framework-core          ← pure patterns, no Spring auto-config. Depends on io.camunda.client + Jackson 2.x.
  ↑ (transitive via)
framework-camunda-starter   → auto-configures ProcessService, VariableMapper, IdempotencyGuard,
                              OutboxRelay, and a Jackson 2.x ObjectMapper (@ConditionalOnMissingBean).
framework-security-starter  → OAuth2 resource server, JWT-roles→authorities, optional CORS.
framework-observability-starter → Micrometer/Prometheus, OTel bridge, MdcCorrelationFilter, FrameworkCounters.
framework-data-starter      → JPA+Flyway conventions, AuditColumnListener, FlywayNamingConventionValidator.
framework-web-starter       → Response<T> envelope + RFC 7807 GlobalExceptionHandler.
framework-test              → ArchUnit rule constants + CamundaScenarioTestBase (test scope in consumers).
service-template            → the scaffold; wires the starters together into a runnable Boot app.
```

Each starter registers itself via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
Several also ship a low-priority `EnvironmentPostProcessor` (registered in
`...boot.env.EnvironmentPostProcessor.imports`) that sets opinionated defaults — e.g. the camunda
starter defaults `camunda.client.mode=saas`, the data starter defaults `hibernate.ddl-auto=validate`
and `open-in-view=false`. These run *before* the context, so they set defaults a service can still
override in its own `application.yml`.

## Architecture invariants (enforced by ArchUnit — don't break them)

`framework-test`'s `ArchitectureRules` exposes six `ArchRule` constants that consuming services wire
into an `@AnalyzeClasses` test. When adding or changing framework code, keep these true:

1. `web..` / `workers..` must not touch `infrastructure..` or `repository..` directly.
2. Only `infrastructure.camunda..` (and `com.aaseya.camunda.framework..` itself) may import
   `io.camunda.client..`. **All engine access goes through the `ProcessService` facade.**
3. `domain..` must not import Spring Web, `jakarta.servlet..`, or `io.camunda..` (pure business logic).
4. `@RestController` classes must not be `@Transactional`.
5. Controller methods must not accept or return JPA `@Entity` types — DTOs/records only.
6. No field injection (`@Autowired` on fields) — constructor injection only.

## The core execution model (`framework-core`)

`BaseWorker<V>` (`framework-core/.../worker/BaseWorker.java`) is the heart of the framework — read it
before touching worker logic. It is a template method: subclasses implement only `varsType()` and
`doWork(V, ActivatedJob)`, and annotate their delegating method with
`@JobWorker(type = "...", autoComplete = false)`. The `final execute()` method owns all framework
concerns in a fixed order: variable binding (typed, with a JSON-string fallback) → MDC push →
idempotency short-circuit → `validate()` hook → `doWork()` → dispatch Camunda command based on the
returned `WorkResult` sealed type (`Completed` / `BusinessError` / `Compensated`) → Micrometer
`framework_job_*` counter → MDC cleanup in `finally`.

The **business-failure vs technical-failure distinction is the central design rule**:

- **Business failure** → return `WorkResult.BusinessError` or throw `BusinessException` → framework
  issues `newThrowErrorCommand(errorCode)` → routed by a BPMN boundary event. Also `NonRetryableException`.
- **Technical failure** → throw `RetryableException` or any unclassified `RuntimeException` → framework
  rethrows so Camunda decrements retries and eventually raises an incident. Override `handleException()`
  to demote a specific driver exception to a `WorkResult`.

Idempotency is automatic: if the job's variables contain a `businessKey`, `IdempotencyGuard` checks
`worker_execution(business_key, element_id)` (composite PK) and silently completes replays.
`OutboxRelay` is the transactional-outbox pattern — write to `process_outbox` in the same tx as the
domain change; a scheduled poller (needs `@EnableScheduling` on the app class) dispatches to Camunda
using `SELECT ... FOR UPDATE SKIP LOCKED`. `AuditableEntity<S extends Enum<S>>` is a state-machine
base that validates transitions via `allowedTransitions(from)` and throws
`IllegalStateTransitionException` on illegal moves.

The two framework tables (`worker_execution`, `process_outbox`) are created by
`service-template/.../db/migration/V1__framework_tables.sql`; consumer migrations start at `V2__`.

## Gotchas

- **Jackson version split**: the framework and Camunda 8.9 use Jackson **2.x**
  (`com.fasterxml.jackson`). Boot 4 auto-configures a Jackson **3.x** `JsonMapper`
  (`tools.jackson.databind`). The camunda starter provides a 2.x `ObjectMapper` bean via
  `@ConditionalOnMissingBean`; if a service defines its own, ensure it is the 2.x type or wiring breaks.
- `FOR UPDATE SKIP LOCKED` in `JdbcOutboxRelay.poll()` is Postgres-specific. H2 in `MODE=PostgreSQL`
  covers most tests, but exercise `poll()` against real Postgres (Testcontainers) when in doubt.
- Version numbers are inconsistent across artifacts: the directory is `0.1.1`, but `pom.xml` and the
  READMEs say `0.1.0-SNAPSHOT`. Treat `pom.xml` as authoritative for the Maven coordinate.
- `DEPLOYMENT-LOCAL.md` is now present. Its instructions are sound but all three of its run paths
  fail against the unmodified code (defects 1–3, 5–6 above). `PROGRESS.md`, which it references, is
  still missing.
- **Verifying the app is running:** the scaffold registers no workers, so healthy and broken
  instances look equally idle. Use `curl /actuator/health` (`{"status":"UP"}`), the single
  `Started Application in …` log line, and the `Successfully applied 1 migration` Flyway line.
  Absence of job-activation logs is expected, not a fault. Triage table in `FRAMEWORK-GUIDE.md` §11.4.

## Where to look

- Full narrative docs, config reference (`framework.camunda.*`, `.security.*`, `.observability.*`,
  `.data.*`), and troubleshooting: root **`README.md`**.
- How to scaffold a new service from `service-template`: root README "Scaffolding a new service" +
  `service-template/README.md`.
- Deploy artifacts: `service-template/Dockerfile` (multi-stage, non-root), `deploy/helm-chart/`
  (generic chart + per-service `values-<service>.yaml` overlays), `.gitlab-ci.yml`.
