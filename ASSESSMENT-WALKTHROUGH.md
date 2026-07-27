# Order Service Sample — Run & Fix Walkthrough

**Date:** 2026-07-27
**Author:** Sriram Badeti
**Purpose:** A complete record of what was done to get `order-service-sample` running on top of the `camunda-process-framework` — the framework we understood, the separate consuming project, the errors hit, the root causes, and the exact fixes. Written so it can be read top-to-bottom to understand and explain the work.

---

## 1. The big picture — two separate projects

There are **two independent Maven projects** on the machine:

| Project | Path | What it is |
|---|---|---|
| **camunda-process-framework** | `Assessment\camunda-process-framework-0.1.1\camunda-process-framework-0.1.1` | A reusable **library + scaffold** (version `0.1.0-SNAPSHOT`). Not a runnable app by itself. |
| **order-service-sample** | `Assessment\order-service-sample` | A **real service** that *consumes* the framework as a dependency, to prove the framework works end-to-end. |

**Key idea:** the sample does **not** live inside the framework. It depends on the framework the same way any team's microservice would — by pulling it from the local Maven repository (`~/.m2`). So the framework must be *installed* into `.m2` first (`mvn install`), and then the sample can resolve it.

```
camunda-process-framework  ──(mvn install)──▶  ~/.m2/repository/com/aaseya/camunda/...
                                                          │
                                                          ▼  (declared as <parent> + dependencies)
                                                 order-service-sample
```

---

## 2. What the framework actually is (what I understood)

`camunda-process-framework` is a **multi-module Spring Boot 4 / Java 21 framework for building Camunda 8.9 SaaS process-orchestrated microservices**. It is version-pinned and fragile on purpose: **Java 21, Spring Boot 4.0.5, Camunda 8.9.0, Jackson 2.x**.

### Modules

```
framework-core                  Pure patterns (no Spring auto-config): BaseWorker, WorkResult,
                                AuditableEntity, IdempotencyGuard, OutboxRelay, ProcessService.
framework-camunda-starter       Auto-configures ProcessService, VariableMapper, IdempotencyGuard,
                                OutboxRelay, and a Jackson 2.x ObjectMapper.
framework-web-starter           Response<T> envelope + RFC-7807 error handler.
framework-data-starter          JPA + Flyway conventions, audit columns, ddl-auto=validate.
framework-observability-starter Micrometer/Prometheus, MDC correlation filter.
framework-security-starter      OAuth2 resource server / JWT (not used by the sample).
framework-test                  ArchUnit rule constants + Camunda scenario test base.
service-template                A bare runnable scaffold teams copy to start a new service.
```

### The 6 architecture rules (enforced by ArchUnit)
1. `web..` / `workers..` must not touch `infrastructure..` or `repository..` directly.
2. **Only `infrastructure.camunda..`** (and the framework itself) may import `io.camunda.client..`. Everything else reaches the engine through the **`ProcessService` facade**.
3. `domain..` must be pure — no Spring Web, no servlet, no `io.camunda`.
4. `@RestController` classes must not be `@Transactional`.
5. Controllers accept/return DTOs/records only — never JPA `@Entity`.
6. Constructor injection only — no `@Autowired` fields.

### The core execution model
`BaseWorker<V>` is the heart. A worker subclass only implements `varsType()` and `doWork(...)`. The framework's `execute()` handles: variable binding → MDC → idempotency short-circuit → validate → `doWork` → dispatch the Camunda command based on the returned `WorkResult` → metrics → cleanup. Business failures vs technical failures are routed differently (throw-error vs retry/incident).

---

## 3. What the sample is

`order-service-sample` wires **one BPMN process — `order-fulfillment`** — through every layer, to show where files go:

```
POST /orders ─▶ OrderController (web)                       returns Response<OrderDto>
              └▶ OrderService (application, @Transactional)
                 ├▶ OrderRepository (repository)            → orders table
                 └▶ ProcessService.start(...)               → Camunda starts "order-fulfillment"
                                                                  │
   Camunda activates the "reserve-inventory" job ◀───────────────┘
              └▶ ReserveInventoryWorker (infrastructure.camunda.worker, extends BaseWorker)
                 └▶ OrderService.reserveInventory(...)       → Order.transition(NEW → RESERVED)
```

Originally run on **H2 in-memory** (the `local` profile); it was later switched to **PostgreSQL 17** — see §10. In both cases it connects to a **Camunda 8.9 SaaS** cluster for the workflow engine.

---

## 4. Environment (already present)

- **Java 21** (Oracle JDK 21.0.10) ✅
- **Maven 3.9.14** ✅
- **Framework already installed** in `~/.m2` as `0.1.0-SNAPSHOT` ✅

So no framework build was needed *initially* — only later, after we fixed a framework bug (see §6.2).

---

## 5. The journey — every step, error, and fix

### Step A — First attempt failed on PowerShell argument quoting
```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
**Error:** `Unknown lifecycle phase ".run.profiles=local"`
**Cause:** PowerShell split the `-D...` argument on the dots.
**Fix:** quote the whole property:
```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

### Step B — Provided Camunda SaaS credentials
The base config defaults to `camunda.client.mode: saas`, so the app needs cluster credentials. We put them in `order-service-sample\src\main\resources\application-local.yml`:
```yaml
camunda:
  client:
    mode: saas
    cloud:
      cluster-id: <cluster-id>
      region: <region>          # e.g. sin-1
    auth:
      client-id: <client-id>
      client-secret: <secret>
```
> ⚠️ These are live secrets sitting in a source file. For a real repo, move them to a gitignored `.env` or environment variables. Never commit them.

### Step C — App started, DB worked, then failed: `ProcessService` bean missing
The app booted, connected to **H2**, ran **Flyway** (`Successfully applied 2 migrations`) — then crashed:
```
Parameter 1 of constructor in OrderService required a bean of type
'com.aaseya.camunda.framework.core.process.ProcessService' that could not be found.
```

**Diagnosis (this is the important one):** I ran with `--debug` to get Spring's *condition evaluation report* and decompiled the framework's auto-config. The framework bean is:
```java
@Bean
@ConditionalOnBean(CamundaClient.class)   // ← only create ProcessService IF a CamundaClient bean exists
public ProcessService processService(CamundaClient client, ...) { ... }
```
The report showed:
```
FrameworkCamundaAutoConfiguration#processService:
   Did not match:
   - @ConditionalOnBean (types: io.camunda.client.CamundaClient) did not find any beans
```
…even though the Camunda SDK *does* create a `CamundaClient`.

**Root cause = Spring auto-configuration ordering.**
`@ConditionalOnBean` only sees beans registered by auto-configs that run **earlier**. The framework auto-config had **no ordering hint**, so Spring fell back to **alphabetical** order:
- `com.aaseya.camunda...FrameworkCamundaAutoConfiguration` (framework)
- `io.camunda.client.spring...CamundaAutoConfiguration` (SDK)

`com.aaseya` sorts *before* `io.camunda`, so the framework was evaluated **before** the SDK registered `CamundaClient` → the condition was false → `ProcessService` was **silently skipped**.

**Why nobody caught it before:** the framework's own `service-template` has **no domain/worker code and never injects `ProcessService`**. `order-service-sample` is the **first code that actually uses it**, so it exposed a latent bug that lived in the framework all along.

### Step D — Same bug again: `IdempotencyGuard` bean missing
After fixing ordering for `CamundaClient`, startup failed one bean later:
```
reserveInventoryWorker required a bean of type
'com.aaseya.camunda.framework.core.idempotency.IdempotencyGuard' — not found
```
Same class of problem: `IdempotencyGuard` is `@ConditionalOnBean(JdbcTemplate.class)`, and Spring's `JdbcTemplateAutoConfiguration` was also ordered *after* the framework. So the framework needed to run after **both** the Camunda SDK **and** the JDBC auto-config.

### Step E — App started cleanly, but `POST /orders` returned 500: BPMN not deployed
```
Command 'CREATE' rejected with code 'NOT_FOUND':
Expected to find process definition with process ID 'order-fulfillment', but none found
```
**Cause:** the sample ships `processes/order-fulfillment.bpmn` but **nothing deployed it** to the cluster. (This *proved the SaaS connection was fine* — we got a real, well-formed API rejection back from the cluster.)
**Fix:** added a config class that deploys the BPMN on startup (see §6.3).

### Step F — Full end-to-end success ✅
```
POST /orders {"productSku":"ABC-123","quantity":2}
 → 201 {"data":{"id":"7b5e...","status":"NEW"}}

GET /orders/7b5e...   (after the worker ran)
 → 200 {"data":{"id":"7b5e...","status":"RESERVED"}}
```
Status transitioned **NEW → RESERVED**, proving the whole chain works.

---

## 6. The three fixes (files changed)

### 6.1 Credentials — `order-service-sample\src\main\resources\application-local.yml`
Added the `camunda.client.cloud.*` and `camunda.client.auth.*` block (Step B).

### 6.2 Framework auto-config ordering — THE REAL FIX
File: `camunda-process-framework-0.1.1\...\framework-camunda-starter\...\FrameworkCamundaAutoConfiguration.java`

**Before:**
```java
@AutoConfiguration
@ConditionalOnClass(CamundaClient.class)
@EnableConfigurationProperties(FrameworkCamundaProperties.class)
public class FrameworkCamundaAutoConfiguration {
```
**After:**
```java
@AutoConfiguration(afterName = {
        "io.camunda.client.spring.configuration.CamundaAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"
})
@ConditionalOnClass(CamundaClient.class)
@EnableConfigurationProperties(FrameworkCamundaProperties.class)
public class FrameworkCamundaAutoConfiguration {
```
This forces the framework's beans to be evaluated **after** `CamundaClient` and `JdbcTemplate` exist, so `@ConditionalOnBean` succeeds.

**Because the framework changed, it must be reinstalled to `.m2`:**
```powershell
cd C:\Users\Sriram.Badeti\Desktop\Assessment\camunda-process-framework-0.1.1\camunda-process-framework-0.1.1
mvn install -DskipTests
```

### 6.3 Deploy the BPMN — new file
File: `order-service-sample\...\infrastructure\camunda\CamundaDeploymentConfig.java`
```java
@Configuration
@Deployment(resources = "classpath:processes/order-fulfillment.bpmn")
public class CamundaDeploymentConfig { }
```
Placed under `infrastructure.camunda` **because it imports `io.camunda.client..`**, which ArchUnit rule #2 only allows in that package. The Camunda SDK's `DeploymentAnnotationProcessor` uploads the BPMN once the client is ready → log line `Deployed Processes: <order-fulfillment:1>`.

---

## 7. How to run it now (final procedure)

**One-time, only if the framework source changed:**
```powershell
cd C:\Users\Sriram.Badeti\Desktop\Assessment\camunda-process-framework-0.1.1\camunda-process-framework-0.1.1
mvn install -DskipTests
```

**Prerequisite (current setup):** the `local` profile now uses **PostgreSQL 17**, so the DB must be running first — start the `postgresql-x64-17` Windows service (see §10). The `order_service` database/role already exist from the one-time setup in §10.

**Run the service:**
```powershell
cd C:\Users\Sriram.Badeti\Desktop\Assessment\order-service-sample
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```
Wait for:
- `Database: jdbc:postgresql://localhost:5432/order_service (PostgreSQL 17.0)`
- `Successfully applied 2 migrations` (Flyway — on a fresh DB; already-applied on later runs)
- `Deployed Processes: <order-fulfillment:1>` (BPMN uploaded)
- `Started OrderServiceApplication in ~10s`

**Verify + test (second terminal):**
```powershell
curl http://localhost:8080/actuator/health
# {"status":"UP"}   ← UP also confirms the Camunda SaaS connection is healthy

curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d '{\"productSku\":\"ABC-123\",\"quantity\":2}'
# 201 -> status "NEW", note the id

curl http://localhost:8080/orders/<id>
# 200 -> status "RESERVED"  (the reserve-inventory worker transitioned it)
```

---

## 8. Talking points for the lead (what this demonstrates)

1. **The sample works end-to-end** on the framework: HTTP → application service → Camunda SaaS → job worker → domain state machine (NEW → RESERVED).
2. **We found and fixed a genuine framework defect**, not a sample mistake: `FrameworkCamundaAutoConfiguration` relied on `@ConditionalOnBean` without an ordering guarantee, so its core beans (`ProcessService`, `IdempotencyGuard`) were silently dropped depending on auto-config order. It was invisible until a service actually consumed those beans — which the framework's own `service-template` never does.
   - **Recommendation:** the `@AutoConfiguration(afterName = …)` fix should be contributed back to the framework repo, and `service-template` (or a test) should be extended to actually inject `ProcessService`/`IdempotencyGuard` so this can't regress.
3. **Config vs functional problems are distinct:** a `health = UP` and a well-formed `NOT_FOUND` from the cluster proved connectivity was correct; the remaining issue was purely "the BPMN wasn't deployed," fixed with a one-line `@Deployment`.
4. **Architecture discipline held:** the new deployment class had to go under `infrastructure.camunda` to satisfy ArchUnit rule #2 — the framework's guardrails guided the correct placement.

---

## 9. Open items / notes
- **Secrets:** the SaaS credentials (and the DB password) are currently inline in `application-local.yml`. Move to a gitignored `.env` or env vars before committing.
- **Framework re-copy:** if the framework is restored from a fresh copy, re-apply the ordering fix (§6.2) and `mvn install` again.
- **Region:** confirm the cluster `region` matches the Camunda console exactly, or the connection fails.
- **Postgres must be started manually** after a reboot — Claude's session cannot start Windows services (no admin). See §10.

---

## 10. Switching the database from H2 to PostgreSQL 17

The project was migrated from the in-memory H2 database to a real **PostgreSQL 17** instance, while keeping the **same run command** (`-Dspring-boot.run.profiles=local`).

### Why it was easy
The project was always Postgres-first: the base `application.yml` already targets Postgres, and the `local` profile was the *override* that swapped in H2. Switching back to Postgres just meant changing that override.

### One-time setup (already done)
1. **Start PostgreSQL 17** (service `postgresql-x64-17`, port 5432). This needs an **elevated** shell — Claude's session cannot start Windows services:
   ```powershell
   net start postgresql-x64-17        # in an "Run as administrator" PowerShell
   ```
2. **Create the role, database, and schema grants** (run once, as the `postgres` superuser). `psql` is not on PATH — use the full path:
   ```powershell
   $psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
   $env:PGPASSWORD = "postgres"
   & $psql -U postgres -h localhost -p 5432 -d postgres -c "CREATE ROLE order_service LOGIN PASSWORD 'order_service';"
   & $psql -U postgres -h localhost -p 5432 -d postgres -c "CREATE DATABASE order_service OWNER order_service;"
   # PostgreSQL 15+ locks down the public schema — the app's role needs it to create tables:
   & $psql -U postgres -h localhost -p 5432 -d order_service -c "GRANT ALL ON SCHEMA public TO order_service; ALTER SCHEMA public OWNER TO order_service;"
   ```
   > The last step is essential on PG 15+. Without it, Flyway fails with *"permission denied for schema public"*.

### Config change — `application-local.yml`
**Before (H2):**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:order_service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    username: sa
    password:
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
```
**After (PostgreSQL 17):**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/order_service
    username: order_service
    password: order_service
  jpa:
    hibernate:
      ddl-auto: validate      # Flyway owns the schema; Hibernate only validates it
```
(The `camunda.client.*` SaaS credentials block stays unchanged. Hibernate auto-detects the Postgres dialect, so no `database-platform` is needed.)

### Verified on Postgres
- Startup log: `Database: jdbc:postgresql://localhost:5432/order_service (PostgreSQL 17.0)`, dialect `PostgreSQLDialect`.
- Flyway created **4 tables**: `orders` (V2), plus framework tables `worker_execution` + `process_outbox` (V1), plus `flyway_schema_history`.
- End-to-end `POST /orders` → `NEW` → worker → `GET` → `RESERVED`.
- **Persistence confirmed by querying Postgres directly:**
  ```sql
  SELECT id, product_sku, quantity, status FROM orders;
  -- cb0b6116-... | PG-777 | 3 | RESERVED
  ```

### How to inspect the data
```powershell
$env:PGPASSWORD = "order_service"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U order_service -h localhost -p 5432 -d order_service -c "\dt"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U order_service -h localhost -p 5432 -d order_service -c "SELECT * FROM orders;"
```
