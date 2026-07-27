# CLAUDE.md — order-service-sample

Guidance for Claude Code (and humans) working in this repository.

## What this is

`order-service-sample` is a **worked example of a microservice built on
`camunda-process-framework`** (`com.aaseya.camunda:camunda-process-framework:0.1.0-SNAPSHOT`).
Its job is to show **where a real project's files and folders go** when you consume the
framework — one BPMN process (`order-fulfillment`) wired end-to-end through every
architectural layer.

It is a **separate Maven project**, not part of the framework build. It consumes the framework
the same way any team's service would: the framework's parent POM and starters are resolved
from the local Maven repo (`~/.m2`), where `mvn install` in the framework repo published them.
`<relativePath/>` on the `<parent>` block forces `.m2` resolution, so this sample deliberately
does **not** live inside the framework repo (whose own rules forbid sample code there).

## Why it exists

The framework ships only a bare `service-template` scaffold with **no domain, worker, or BPMN
code**. That scaffold connects to Camunda but never actually *uses* the framework's business
beans. This sample is the first thing that exercises the framework for real — controller →
service → `ProcessService` → Camunda → `BaseWorker` → domain state machine — so it doubles as
an **integration proof** that the framework's pieces fit together. (In doing so it surfaced a
real framework defect — see "Framework defect this sample exposed" below.)

## The one process, end-to-end

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

## Folder layout (the point of the sample)

```
src/main/java/com/aaseya/orders/
├── OrderServiceApplication.java     @SpringBootApplication + @EnableScheduling
├── web/                             inbound HTTP adapter — DTOs only, no @Transactional
│   ├── OrderController.java         returns Response<OrderDto>
│   └── dto/                         CreateOrderRequest, OrderDto (records)
├── application/OrderService.java    orchestration — @Transactional, injects ProcessService
├── domain/                          pure business logic (no Spring Web / io.camunda)
│   ├── Order.java                   @Entity extends AuditableEntity<OrderStatus>
│   └── OrderStatus.java             NEW → RESERVED state machine
├── repository/OrderRepository.java  Spring Data JPA
└── infrastructure/camunda/          the ONLY place io.camunda.client may be imported
    ├── CamundaDeploymentConfig.java @Deployment(...) — uploads the BPMN on startup  [ADDED]
    └── worker/ReserveInventoryWorker.java + ReserveInventoryVars.java
src/main/resources/
├── application.yml                  base: Postgres datasource + Camunda SaaS via env vars
├── application-local.yml            local profile — DB creds + Camunda SaaS secrets
├── processes/order-fulfillment.bpmn
└── db/migration/                    V1 (framework tables) + V2 (orders table)
src/test/java/.../architecture/OrderArchitectureTest.java   wires the 6 framework ArchUnit rules
```

## Architecture rules that constrain this repo (from the framework, enforced by ArchUnit)

1. `web..` must not touch `infrastructure..`/`repository..` directly — go via `application`.
2. **Only `infrastructure.camunda..` may import `io.camunda.client..`.** This is why both the
   worker AND `CamundaDeploymentConfig` live there. Everything else uses the `ProcessService` facade.
3. `domain..` is pure — no Spring Web, no servlet, no `io.camunda`.
4. `@RestController` must not be `@Transactional` (the tx lives in `OrderService`).
5. Controllers accept/return DTOs/records only — never the `Order` entity.
6. Constructor injection only — no `@Autowired` fields.

`OrderArchitectureTest` fails the build if any of these are broken.

## How to run (current setup — PostgreSQL 17)

The `local` profile targets **PostgreSQL 17** on `localhost:5432/order_service`.
(The base `application.yml` is Postgres-first; the `local` profile originally overrode it with
H2 but was switched to Postgres on 2026-07-27.)

**Prerequisites**
- Java 21, Maven, and the framework installed to `~/.m2` (`mvn install -DskipTests` in the framework repo).
- **PostgreSQL 17 running** (`postgresql-x64-17` service, port 5432). Start it in an elevated shell:
  `net start postgresql-x64-17`.
- Database `order_service` + role `order_service`/`order_service` must exist, and the role must
  own the `public` schema (PG 15+ locks it down). One-time setup (`psql` at
  `C:\Program Files\PostgreSQL\17\bin\psql.exe`):
  ```
  CREATE ROLE order_service LOGIN PASSWORD 'order_service';
  CREATE DATABASE order_service OWNER order_service;
  -- then, connected to order_service:
  GRANT ALL ON SCHEMA public TO order_service;  ALTER SCHEMA public OWNER TO order_service;
  ```

**Run**
```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"   # quote the -D arg in PowerShell
```
Healthy startup shows: `Database: jdbc:postgresql://.../order_service (PostgreSQL 17.0)`,
`Successfully applied 2 migrations`, `Deployed Processes: <order-fulfillment:1>`,
`Started OrderServiceApplication`.

**Test end-to-end**
```powershell
curl http://localhost:8080/actuator/health        # {"status":"UP"} — also confirms Camunda SaaS is reachable
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d '{\"productSku\":\"ABC-123\",\"quantity\":2}'
curl http://localhost:8080/orders/<id>            # status flips NEW -> RESERVED after the worker runs
```

**Architecture tests only (no DB, no Docker):** `mvn test`.

## Changes made to get this running (2026-07-27)

1. **Camunda SaaS credentials** added to `application-local.yml` (`camunda.client.cloud.*` +
   `camunda.client.auth.*`). The base config defaults to `mode: saas`, so a cluster is required.
2. **`CamundaDeploymentConfig.java` added** under `infrastructure.camunda` — the sample shipped
   the BPMN but nothing deployed it, so `POST /orders` failed with
   `NOT_FOUND: process 'order-fulfillment'`. `@Deployment(resources = "classpath:processes/order-fulfillment.bpmn")`
   makes the Camunda SDK upload it on startup. Placed in `infrastructure.camunda` to satisfy rule #2.
3. **Database switched from H2 to PostgreSQL 17** in `application-local.yml` (see "How to run").

## Framework defect this sample exposed

`OrderService` is the **first code anywhere that injects `ProcessService`**, and
`ReserveInventoryWorker` the first that injects `IdempotencyGuard`. Both are framework
auto-config beans guarded by `@ConditionalOnBean` — and the framework's
`FrameworkCamundaAutoConfiguration` had **no auto-configuration ordering hint**, so it was
evaluated (alphabetically, `com.aaseya…`) *before* the Camunda SDK / Spring JDBC registered
`CamundaClient` / `JdbcTemplate`. Result: those framework beans were **silently skipped**, and
startup failed with "no qualifying bean of type ProcessService / IdempotencyGuard".

The framework was fixed (added `@AutoConfiguration(afterName = { CamundaAutoConfiguration,
JdbcTemplateAutoConfiguration })`) and reinstalled to `.m2`. If the framework is restored from a
fresh copy, that fix must be re-applied and `mvn install` re-run, or this sample won't start.
Full write-up: `..\ASSESSMENT-WALKTHROUGH.md`.

## Gotchas

- **PowerShell**: quote the `-D` arg (`"-Dspring-boot.run.profiles=local"`) or Maven mis-parses it.
- **Secrets**: `application-local.yml` contains live Camunda secrets and the DB password — do not
  commit it; move to a gitignored `.env` before sharing.
- **Postgres start needs admin**: a non-elevated session cannot start the Windows service.
- **Flyway owns the schema** (`ddl-auto=validate`); consumer migrations start at `V2__`
  (`V1__` is the framework tables, copied in).
```
