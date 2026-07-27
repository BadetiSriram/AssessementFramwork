# service-template

A bare scaffold for building a new service on `camunda-process-framework`. This module is
a starting point, not a demo. It contains no sample BPMN, no sample workers, and no domain
code — those are your responsibility.

## What is included

- `Application.java` — Spring Boot entry point with `@EnableScheduling` (required by
  `JdbcOutboxRelay`'s scheduled poller).
- `application.yml` — production-shaped config wired to environment variables for the
  Camunda SaaS cluster, Postgres datasource, and actuator endpoints.
- `application-local.yml` — overrides the datasource to H2 in-memory for local development
  without a real Postgres instance.
- `db/migration/V1__framework_tables.sql` — Flyway migration that creates `worker_execution`
  (idempotency guard) and `process_outbox` (transactional outbox) using TEXT-typed payload
  for H2/Postgres compatibility.

## How to use this scaffold

1. Copy this module into your target repository.
2. Rename the base package `com.aaseya.camunda.service.template` to
   `com.<yourorg>.<yourservice>` in all Java sources and the `pom.xml` `<mainClass>`.
3. Create a `.env` file at the repo root (see `.env.example`) and populate it with your
   Camunda SaaS cluster credentials and Postgres connection details.
4. Add your workers under `infrastructure/camunda/`, your domain logic under `domain/`, and
   your BPMN files under `src/main/resources/`.
5. Run locally:
   ```
   mvn spring-boot:run -pl service-template -Dspring-boot.run.profiles=local
   ```

## What is NOT included and why

No sample BPMN files, workers, domain aggregates, REST controllers, or state machines are
provided. This framework delivers reusable infrastructure; your business logic is added by
you downstream. Mixing sample code into the scaffold would make copying the module harder and
violate the framework's clean separation of concerns.

## References

- Root `DEPLOYMENT-LOCAL.md` — prerequisites, build, local run, configuration reference, framework patterns, and cardinal invariants.
