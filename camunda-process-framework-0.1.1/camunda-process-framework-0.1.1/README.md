# camunda-process-framework

Multi-module Spring Boot 4 / Java 21 base framework for building process-orchestrated microservices on **Camunda 8.9 SaaS**.

The framework ships seven reusable modules (patterns, five opinionated Boot starters, and a test-support library) plus a runnable service scaffold, a hardened Dockerfile, a generic Helm chart, and a reference GitLab CI pipeline. Developers scaffold a new service from the template and add their own BPMN, workers, and domain code.

- **Version:** `0.1.0-SNAPSHOT`
- **Group ID:** `com.aaseya.camunda`
- **Java:** 21
- **Spring Boot:** 4.0.5
- **Camunda:** 8.9.0 (SaaS, REST protocol)

## Modules

| Module | Purpose |
|---|---|
| `framework-core` | Reusable patterns — `BaseWorker<V>` template method for `@JobWorker`, `ProcessService` facade over `CamundaClient`, `VariableMapper` (Jackson 2.x anti-corruption layer), `IdempotencyGuard` + JDBC impl, `OutboxRelay` + JDBC impl, `AuditableEntity` state-machine base, exception types, MDC key constants. |
| `framework-camunda-starter` | Spring Boot auto-configuration. Provides `ProcessService`, `VariableMapper`, `IdempotencyGuard`, `OutboxRelay`, and a Jackson 2.x `ObjectMapper` bean (via `@ConditionalOnMissingBean`). Exposes `framework.camunda.*` properties; defaults `camunda.client.mode=saas` via a low-priority `EnvironmentPostProcessor`. |
| `framework-security-starter` | OAuth2 resource server (Spring Security 7). `JwtRealmRolesAuthenticationConverter` maps a configurable JWT claim path (default `realm_access.roles`, Keycloak-shape) to `ROLE_*` authorities. Optional CORS bean gated on servlet-API presence + `framework.security.cors.enabled`. |
| `framework-observability-starter` | Micrometer + Prometheus registry, OpenTelemetry tracing bridge, `MdcCorrelationFilter` (`X-Correlation-Id` + `X-Tenant-Id` → MDC + echoed response headers), `FrameworkCounters` helper for `<domain>_<state>_total` business metrics. |
| `framework-data-starter` | JPA + Flyway conventions via `EnvironmentPostProcessor` defaults (`hibernate.ddl-auto=validate`, `open-in-view=false`). `AuditColumnListener` reflectively stamps `createdAt`/`updatedAt`/`createdBy`/`updatedBy` on JPA entities. `FlywayNamingConventionValidator` enforces `V<version>__<snake_case>.sql` naming at `BEFORE_MIGRATE`. |
| `framework-test` | Six ArchUnit `ArchRule` constants for layering, Camunda-boundary, entity-DTO, and constructor-injection enforcement. `CamundaScenarioTestBase` wraps `@CamundaSpringProcessTest`. `JdbcTemplateTestFactory` builds H2-backed `JdbcTemplate`s. `MdcAssertions` helpers catch leaked MDC context. |
| `framework-web-starter` | REST API layer for services that expose HTTP endpoints. `Response<T>` envelope with `Meta` (correlation ID + timestamp). `GlobalExceptionHandler` (`@RestControllerAdvice`) maps framework exceptions to RFC 7807 `ProblemDetail`: `BusinessException` → 422, `RetryableException` → 503 with `Retry-After`, `NonRetryableException` → 500, `MethodArgumentNotValidException` / `ConstraintViolationException` → 400 with field errors. Brings in `spring-boot-starter-validation` transitively. Opt-out via `framework.web.exception-handler-enabled=false`. |
| `service-template` | Runnable scaffold. `@SpringBootApplication` + `@EnableScheduling`, `application.yml` driven by environment variables, five profile overlays (`local` H2, `dev`, `qa`, `uat`, `prod` — differ in log verbosity and actuator exposure), and a Flyway migration that creates the two framework tables (`worker_execution`, `process_outbox`). No sample BPMN, workers, or domain code — those are added by the consuming team. |

## Repository layout

```
camunda-process-framework/
├── pom.xml                          parent POM (Spring Boot 4.0.5 parent, deps pinned)
├── framework-core/                  patterns library
├── framework-camunda-starter/       auto-config
├── framework-security-starter/      JWT + CORS
├── framework-observability-starter/ Micrometer + MDC + OTel
├── framework-data-starter/          JPA + Flyway conventions
├── framework-test/                  ArchUnit rules + test harness
├── framework-web-starter/           Response<T> envelope + RFC 7807 GlobalExceptionHandler
├── service-template/                scaffold service (copy this to start a new service)
├── deploy/
│   └── helm-chart/                  generic chart + per-service overlay + README
├── tools/
│   └── check-bpmn-integrity.sh      BPMN reference-integrity script
├── .gitlab-ci.yml                   reference CI pipeline (9 stages)
├── .env.example                     credential template
├── .gitignore, .editorconfig
├── FRAMEWORK-GUIDE.md               the complete guide — what/why/where/when, deep dives, known gaps
├── UNDERSTANDING.md                 short orientation walkthrough
└── README.md                        this file
```

> 📖 For the full narrative — why the framework exists, what you gain by adopting it, a line-by-line
> walkthrough of `BaseWorker`, and an honest list of current gaps — see **[`FRAMEWORK-GUIDE.md`](FRAMEWORK-GUIDE.md)**.

## Prerequisites

- **Java 21** — `java -version` must report 21.x.
- **Maven 3.9+** — `mvn -v`.
- **Camunda 8.9 SaaS cluster** with cluster ID, region, and OAuth client credentials. Sign in at [Camunda Cloud](https://console.camunda.io) to provision.
- **PostgreSQL 16** for production. Local development uses H2 in-memory (bundled at test scope).
- Optional: **Docker + Helm** for the deploy artifacts. **`xmllint`** for BPMN validation.

## Build

From the repository root:

```
mvn verify
```

Expected: `BUILD SUCCESS` across eight modules with 183 tests passing (0 failures). The build produces:

- Jar artifacts for the six library modules.
- A fat, layered Boot jar at `service-template/target/service-template-0.1.0-SNAPSHOT.jar`.

> **Docker must be running.** `framework-test` starts a `camunda/camunda:8.9.0` container via
> Testcontainers; without a Docker daemon the build fails at that module and the two after it are
> skipped. If Docker is unavailable, use `mvn install -DskipTests`, or
> `mvn verify -Dtest='!CamundaScenarioTestBaseTest' -DfailIfNoSpecifiedTests=false`.

> **No coverage reports are produced.** JaCoCo is declared only in the parent POM's
> `<pluginManagement>` and is not bound in any module's `<build>`, so `prepare-agent`, `report` and
> `check` never run — there is no `target/site/jacoco/` output and the documented 80%/90% thresholds
> are not enforced. Same for Failsafe, so `*IT.java` tests would not execute. See
> [`FRAMEWORK-GUIDE.md`](FRAMEWORK-GUIDE.md) §15.10.

## Run the scaffold locally

1. Copy `.env.example` to `.env` at the repo root and fill in your cluster values:

   ```
   CAMUNDA_CLIENT_ID=<oauth client id>
   CAMUNDA_CLIENT_SECRET=<oauth client secret>
   CAMUNDA_CLUSTER_ID=<your cluster id>
   CAMUNDA_REGION=<your region, e.g. bru-2>
   ```

2. Load the env vars and start the scaffold with the `local` profile (H2 in-memory):

   **Bash / Git Bash / WSL:**
   ```
   set -a && source .env && set +a
   mvn spring-boot:run -pl service-template -Dspring-boot.run.profiles=local
   ```

   **PowerShell:**
   ```
   Get-Content .env | ForEach-Object {
       if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') {
           [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2])
       }
   }
   mvn spring-boot:run -pl service-template "-Dspring-boot.run.profiles=local"
   ```

3. Verify — see "Checking whether the application is running" below.

### Without a Camunda Cloud account

The `localdocker` profile runs against a self-managed Camunda 8.9 in Docker instead of SaaS. Camunda
can run as a single container backed by H2 rather than Elasticsearch — the same arrangement the
`camunda-process-test` harness uses:

```
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

mvn spring-boot:run -pl service-template -Dspring-boot.run.profiles=localdocker
```

The engine takes port 8080, so the service listens on **8081** under this profile. No `.env` or
credentials required.

## Checking whether the application is running

The scaffold registers no workers and pulls no jobs, so a healthy instance and a broken one look
equally quiet. Check explicitly rather than inferring from the absence of errors.

**The definitive check:**

```
curl -s http://localhost:8080/actuator/health
```

| Response | Meaning |
|---|---|
| `{"groups":["liveness","readiness"],"status":"UP"}` | Running correctly |
| `{"status":"DOWN", ...}` | Started, but a contributor is failing — usually the Camunda client |
| nothing, `curl` exit **7** | Not running (connection refused) |
| nothing, `curl` exit **28** | Still starting — retry for ~30s before concluding it is stuck |

**The rest of the checklist:**

```
# process is alive
docker ps --filter name=svc-tmpl --format "{{.Names}}: {{.Status}}"   # container
netstat -ano | findstr :8080                                          # mvn spring-boot:run

# startup actually completed (exactly one line on success)
docker logs svc-tmpl 2>&1 | grep "Started Application"

# framework tables were created — without them IdempotencyGuard and OutboxRelay fail at first use
docker logs svc-tmpl 2>&1 | grep "Successfully applied"

# container health (image ships a HEALTHCHECK; 'starting' is normal for ~45s)
docker inspect --format='{{.State.Health.Status}}' svc-tmpl

# engine reachable
curl -s http://localhost:8080/v2/topology | grep -o '"health":"[a-z]*"'
```

**What you should NOT expect:** no job-activation logs. The scaffold ships no `@JobWorker`, so
silence there is correct and does not indicate a broken cluster connection.

**If the process exits immediately with code 1**, read the first `Caused by:` in the log. Bad or
missing `CAMUNDA_*` credentials fail during context initialisation — not later at the first engine
call — so a process that stays up has already authenticated successfully.

Full triage table, including the known startup defects and their fixes:
[`FRAMEWORK-GUIDE.md`](FRAMEWORK-GUIDE.md) §11.4.

## Scaffolding a new service

`service-template/` is a scaffold, not a demo. To create a new service:

1. Copy the `service-template/` directory to your target repository, renaming it (for example `booking-service/`).
2. Rename the base Java package `com.aaseya.camunda.service.template` to `com.<yourorg>.<yourservice>` across all sources and in the `pom.xml` `<mainClass>` configuration.
3. If the module lives in the same monorepo, register it in the parent `pom.xml` `<modules>` list. If it lives in a separate repository, consume the two framework artifacts as external dependencies:
   ```xml
   <dependency>
     <groupId>com.aaseya.camunda</groupId>
     <artifactId>framework-camunda-starter</artifactId>
     <version>0.1.0-SNAPSHOT</version>
   </dependency>
   ```
   The starter transitively brings `framework-core`. Add `framework-security-starter`, `framework-observability-starter`, and `framework-data-starter` as needed. Add `framework-test` at `<scope>test</scope>` to inherit the ArchUnit rule set.
4. Place BPMN files under `src/main/resources/processes/`.
5. Add `@JobWorker` classes under `<yourpkg>.workers/`, extending `BaseWorker<V>`.
6. Add domain aggregates under `<yourpkg>.domain/`, extending `AuditableEntity<StatusEnum>` for validated status state machines.
7. Add REST controllers under `<yourpkg>.web/` — return DTOs only, entities never cross the web boundary.
8. Add Flyway migrations under `src/main/resources/db/migration/V2__<name>.sql` and later. V1 is the framework's own migration and already runs.

## Framework patterns — quick reference

- **`BaseWorker<V>`** — extend and implement `Class<V> varsType()` and `WorkResult doWork(V vars, ActivatedJob job)`. The framework handles variable deserialisation, MDC push, idempotency check, error classification (business error → `newThrowErrorCommand`; unclassified `RuntimeException` → rethrow so Camunda retries decrement into an incident), and Micrometer counters.

- **`ProcessService`** — inject to start processes, correlate messages, or complete user tasks. Search-index operations apply bounded exponential-backoff retries internally (Camunda's user-task search is eventually consistent).

- **`VariableMapper`** — inject to serialise or deserialise process variables to/from Java records. Jackson 2.x with `FAIL_ON_UNKNOWN_PROPERTIES=false` and `JavaTimeModule`. Null required record components throw `VariableBindingException`.

- **`IdempotencyGuard`** — JDBC implementation uses `worker_execution(business_key, element_id)` with a composite primary key. `BaseWorker` calls it automatically.

- **`OutboxRelay`** — transactional outbox. Write to `process_outbox` in the same transaction as your domain change; a scheduled poller dispatches to Camunda asynchronously, using `SELECT ... FOR UPDATE SKIP LOCKED` for safe parallel workers.

- **`AuditableEntity<S extends Enum<S>>`** — extend on a domain aggregate. Override `allowedTransitions(S from)`, `getStatus()`, `setStatus(S)`, `appendAuditNote(...)`. Illegal transitions throw `IllegalStateTransitionException`.

- **ArchUnit rules** (six constants in `framework-test`'s `ArchitectureRules`):
  1. `web..` and `workers..` must not access `infrastructure..` or repositories.
  2. Only `infrastructure.camunda..` (and the framework itself) may import `io.camunda.client..`.
  3. `domain..` must not import Spring web, `jakarta.servlet..`, or `io.camunda..`.
  4. `@RestController` classes must not be `@Transactional`.
  5. Controller methods must not expose `@Entity` types.
  6. No field injection — constructor injection only.

## Cardinal invariants

1. **BPMN is the source of truth for every lifecycle.** Each automated step maps 1:1 to a `@JobWorker`; each human step is a Camunda user task assigned to a persona candidate group equal to a Keycloak (or other IdP) group name.
2. **Workers and controllers are inbound adapters.** No business rules — rules live in `domain/`.
3. **Orchestration-based saga.** Services never call each other directly for saga steps; Camunda mediates every hop. Rollback = BPMN compensation boundary events + a `<step>-compensate` worker, not hand-coded rollback logic.
4. **Domain code never imports `io.camunda.client.*`.** All engine access goes through the `ProcessService` facade. Enforced by ArchUnit.
5. **Business failure ≠ technical failure.** Business failures throw `newThrowErrorCommand` with an error code routed by a BPMN boundary event. Technical failures let retries decrement into an incident.

## Configuration reference

`framework.camunda.*` (defaults shown):

```yaml
framework:
  camunda:
    multi-tenant: false          # true only if your cluster has multi-tenancy enabled
    worker:
      max-jobs-active: 32        # max simultaneously-activated jobs per worker
      poll-interval: PT30S       # polling interval when in-flight < max-jobs-active
      retry-backoff: PT5S        # base retry back-off for technical failures
      default-retries: 3         # retry budget for jobs without an explicit BPMN retry count
```

`framework.security.*`:

```yaml
framework:
  security:
    cors:
      enabled: false             # opt-in
      allowed-origins: []
      allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
      allowed-headers: ["*"]
      allow-credentials: false
      max-age: PT1H
    jwt:
      roles-claim: realm_access.roles  # dot-path into JWT claims
      role-prefix: ROLE_
```

`framework.observability.*`:

```yaml
framework:
  observability:
    mdc:
      enabled: true
      header-name: X-Correlation-Id
      generate-if-absent: true
      tenant-id-header-name: X-Tenant-Id
    metrics:
      business-counter-prefix: ""       # your domain name, used as prefix for FrameworkCounters
```

`framework.data.*`:

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

## Deployment

- **Dockerfile** — `service-template/Dockerfile`. Multi-stage build (`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`), layered jars via `spring-boot-maven-plugin`, non-root `USER 1000`, `HEALTHCHECK` on `/actuator/health/liveness`. Build with `docker build -f service-template/Dockerfile -t <your-registry>/service-template:<tag> .` from the repo root.
- **Helm chart** — `deploy/helm-chart/`. Generic chart with per-service `values-<service>.yaml` overlays. Includes probes wired to Actuator, HPA on CPU + memory + optional custom metrics, `PodDisruptionBudget`, `NetworkPolicy` (egress-only), `ServiceMonitor` for Prometheus, external-secrets integration via `envFrom.secretRef`. See `deploy/helm-chart/README.md` for the values reference.
- **GitLab CI** — `.gitlab-ci.yml`. Nine stages: `build → test → coverage-gate → sonar → bpmn-integrity → image → image-scan (Trivy) → helm-lint → deploy-dev (manual) → smoke-dev`. Uses `extends:` inheritance for shared job config. Requires the CI/CD variables listed in the file header.

## Troubleshooting

- **`NoSuchBeanDefinitionException: ObjectMapper`** — The framework provides a Jackson 2.x `ObjectMapper` bean via `@ConditionalOnMissingBean`. If your service supplies its own via `@Bean`, ours backs off. Ensure the injected type is `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2.x), not Boot 4's auto-configured `tools.jackson.databind.JsonMapper` (Jackson 3.x). The framework and Camunda 8.9 both use Jackson 2.x.

- **Health endpoint returns DOWN with a Camunda client error** — Check that the four `CAMUNDA_*` env vars are actually loaded into the process environment; loader syntax depends on your shell. `CAMUNDA_REGION` is required and case-sensitive.

- **Flyway migration fails on a non-Postgres, non-H2 database** — The shipped `V1__framework_tables.sql` uses `TEXT` for the `payload` column for cross-database compatibility. If your production Postgres needs native `jsonb`, add a follow-up migration:
  ```sql
  ALTER TABLE process_outbox
    ALTER COLUMN payload TYPE jsonb USING payload::jsonb;
  ```

- **Workers do not pick up jobs** — Verify (a) `@EnableScheduling` is present on your `@SpringBootApplication` class (required for the outbox poller, and easy to forget), (b) your `@JobWorker` classes are annotated and registered as Spring beans, and (c) the `type` attribute on the BPMN `<zeebe:taskDefinition>` matches the string in `@JobWorker(type = "...")`.

- **`FOR UPDATE SKIP LOCKED` fails in tests** — `JdbcOutboxRelay.poll()` uses Postgres-specific locking. H2 in `MODE=PostgreSQL` accepts most Postgres syntax, but if a test exercises `poll()` and it fails, run those tests against a real Postgres instance (Testcontainers works well).

## Extending the framework

Framework enhancements go through the same phase discipline that built it:

- **Phase 4 (Adoption)** — next planned work. Cookbook recipes covering common patterns (saga step, user-task step, compensation path, decision-routed gateway, starting a process from a domain state change) and a scaffolder script or Maven archetype.
- Version bumps to Spring Boot, Camunda, Postgres, or Java require explicit approval — the compatibility matrix is version-sensitive.
- Contributions to `framework-core` must be paired with an ArchUnit test if they add a new rule shape, and a unit test if they add new behaviour. Coverage gates: 80% overall, 90% on `framework-core` domain packages.

## Maintainer

`jain.sanjay@aaseya.com`
