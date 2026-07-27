# order-service-sample

A worked example of a service built on **camunda-process-framework**, created to show
**where a real project's files and folders go**. It is a *separate* Maven project that
consumes the framework as a dependency — it deliberately does **not** live inside the
framework repo (whose own rules forbid sample code there).

One process, `order-fulfillment`, is wired end-to-end through every architectural layer:

```
POST /orders ─▶ OrderController (web)
              └▶ OrderService (application, @Transactional)
                 ├▶ OrderRepository (repository)  → orders table
                 └▶ ProcessService.start(...)      → Camunda starts "order-fulfillment"
                                                        │
   Camunda activates the "reserve-inventory" job ◀─────┘
              └▶ ReserveInventoryWorker (infrastructure.camunda.worker, extends BaseWorker)
                 └▶ OrderService.reserveInventory(...) → Order.transition(NEW → RESERVED)
```

## Folder layout (the point of this sample)

```
src/main/java/com/aaseya/orders/
├── OrderServiceApplication.java     @SpringBootApplication + @EnableScheduling
├── web/                             inbound HTTP adapter — DTOs only, no @Transactional
│   ├── OrderController.java         returns Response<OrderDto>
│   └── dto/                         CreateOrderRequest, OrderDto (records)
├── application/                     orchestration — @Transactional, injects ProcessService
│   └── OrderService.java
├── domain/                          pure business logic — no Spring Web / io.camunda
│   ├── Order.java                   @Entity extends AuditableEntity<OrderStatus>
│   └── OrderStatus.java
├── repository/                      Spring Data JPA
│   └── OrderRepository.java
└── infrastructure/camunda/worker/   the ONLY place io.camunda.client may be imported
    ├── ReserveInventoryWorker.java  extends BaseWorker<ReserveInventoryVars>
    └── ReserveInventoryVars.java
src/main/resources/
├── application.yml / application-local.yml
├── processes/order-fulfillment.bpmn
└── db/migration/                    V1 (framework tables, copied) + V2 (orders table)
src/test/java/com/aaseya/orders/
└── architecture/OrderArchitectureTest.java   wires all 6 framework ArchUnit rules
```

### Why workers live under `infrastructure/camunda/`

A `BaseWorker` subclass must import `io.camunda.client` types (`ActivatedJob`, `JobClient`,
`@JobWorker`). ArchUnit rule #2 allows that **only** under `..infrastructure.camunda..`. The
framework's two READMEs disagree (root says `workers/`, the module README says
`infrastructure/camunda/`) — the arch rule is the tiebreaker. `OrderArchitectureTest` fails
if a worker is placed anywhere else. Everything else reaches the engine through the
`ProcessService` facade (framework code, not the raw client), so it can be injected anywhere
except the pure `domain` layer.

## Build & test

Prerequisite (one-time): install the framework to your local Maven repo so this project can
resolve it. From the **framework repo root**:

```bash
mvn install -DskipTests
```

Then, from **this** directory:

```bash
mvn test        # compiles against the framework and runs the ArchUnit rules — no Docker
```

Success means: it compiles against the real framework signatures, and all six architecture
rules pass (proving the layout is correct).

## Run locally (optional)

Needs the four `CAMUNDA_*` variables loaded (copy `.env.example` → `.env`). Uses H2, no
Postgres required:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Expect `Successfully applied 2 migrations` (Flyway) and `/actuator/health` → `{"status":"UP"}`.
The service registers one worker but pulls no jobs until you `POST /orders` to start a
process instance.

## Not included (one increment each, on top of this)

- **Transactional outbox**: start the process via `OutboxRelay.publishStart(...)` inside the
  same transaction as the order save, instead of calling `ProcessService.start` directly.
- **Saga compensation**: a `<step>-compensate` worker returning `WorkResult.compensated()`.
- **Security**: add `framework-security-starter` + a `SecurityFilterChain` bean for JWT.
- **Scenario test**: a `CamundaScenarioTestBase` subclass that deploys the BPMN and asserts
  the instance completes (needs Docker for the `camunda/camunda:8.9.0` Testcontainer).
```
