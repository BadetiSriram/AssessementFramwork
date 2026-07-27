# How to Run — camunda-process-framework (fixed working copy)

A practical runbook for **this** copy of the framework. Every command below was executed and
verified on a Windows 11 / PowerShell machine on 2026-07-23.

> **This is not the pristine version your lead handed over.** Six defects prevented the original
> from starting or containerising at all; they are fixed here. See
> [What changed from the original](#what-changed-from-the-original) at the bottom.
>
> Related docs: `README.md` (project overview) · `DEPLOYMENT-LOCAL.md` (the original guide — its
> three paths do **not** work against the unmodified code) · `FRAMEWORK-GUIDE.md` (the exhaustive
> reference, §11.4 for triage) · `CLAUDE.md` (engineering cheat-sheet)

---

## Contents

- [TL;DR — the three commands](#tldr--the-three-commands)
- [Prerequisites](#prerequisites)
- [Step by step](#step-by-step)
- [Verify it is running](#verify-it-is-running)
- [Stopping](#stopping)
- [Three ways to run it](#three-ways-to-run-it)
- [Port map](#port-map)
- [Troubleshooting](#troubleshooting)
- [What changed from the original](#what-changed-from-the-original)
- [What to do next](#what-to-do-next)

---

## TL;DR — the three commands

From the repository root, with Docker Desktop running:

```powershell
mvn install -DskipTests                    # 1. build          (~15s)
docker start camunda-local                 # 2. start engine   (~25s to healthy)
mvn spring-boot:run -pl service-template "-Dspring-boot.run.profiles=localdocker"   # 3. run
```

Then in a **second** terminal:

```powershell
curl.exe -s http://localhost:8081/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

No Camunda Cloud account or credentials required.

---

## Prerequisites

| Tool | Required | Check |
|---|---|---|
| Java JDK | 21.x | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker Desktop | running | `docker version` |

Verified working on: Java 21.0.10, Maven 3.9.14, Docker 29.6.2.

**Docker is mandatory**, not optional — the engine runs in it, and `mvn verify` needs it too
(see [step 1](#1--build)). The original README lists Docker as optional; that is wrong.

---

## Step by step

### 1 — Build

```powershell
cd C:\Users\Sriram.Badeti\Desktop\Assessment\camunda-process-framework-0.1.1\camunda-process-framework-0.1.1
mvn install -DskipTests
```

Expected — all 9 reactor entries `SUCCESS`, about 15 seconds:

```
[INFO] camunda-process-framework .......................... SUCCESS [  0.399 s]
[INFO] framework-core ..................................... SUCCESS [  4.178 s]
[INFO] framework-camunda-starter .......................... SUCCESS [  0.917 s]
[INFO] framework-security-starter ......................... SUCCESS [  0.608 s]
[INFO] framework-observability-starter .................... SUCCESS [  1.368 s]
[INFO] framework-data-starter ............................. SUCCESS [  0.950 s]
[INFO] framework-test ..................................... SUCCESS [  2.265 s]
[INFO] framework-web-starter .............................. SUCCESS [  0.985 s]
[INFO] service-template ................................... SUCCESS [  2.406 s]
[INFO] BUILD SUCCESS
```

**Optional — full test suite** (needs Docker; ~3 minutes because one test boots a real Camunda
container):

```powershell
mvn verify
```

Expected: `BUILD SUCCESS`, **183 tests, 0 failures**.

> Without Docker this fails at `framework-test` and skips the two modules after it. Workaround:
> `mvn verify "-Dtest=!CamundaScenarioTestBaseTest" "-DfailIfNoSpecifiedTests=false"`
>
> Note: no coverage reports are produced. JaCoCo is never actually bound to a module, so the
> documented 80%/90% gate does not run. Not a build problem — just don't expect
> `target/site/jacoco/` to exist.

### 2 — Start the Camunda engine

The container already exists on this machine:

```powershell
docker start camunda-local
```

<details>
<summary><strong>First time on a new machine — create it instead</strong></summary>

Camunda 8.9 normally wants Elasticsearch. This runs it as a single self-contained container on
H2 instead — the same arrangement the `camunda-process-test` harness uses:

```powershell
docker run -d --name camunda-local -p 26500:26500 -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=broker,consolidated-auth,security `
  -e CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=rdbms `
  -e CAMUNDA_DATABASE_TYPE=rdbms `
  -e "CAMUNDA_DATABASE_URL=jdbc:h2:mem:camunda;DB_CLOSE_DELAY=-1;MODE=PostgreSQL" `
  -e CAMUNDA_DATABASE_USERNAME=sa -e CAMUNDA_DATABASE_PASSWORD= `
  -e ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME=io.camunda.exporter.rdbms.RdbmsExporter `
  -e CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true `
  -e CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false `
  camunda/camunda:8.9.0
```

(Bash / Git Bash: replace the trailing backticks with `\`.)
</details>

### 3 — Wait for the engine

```powershell
curl.exe -s http://localhost:8080/v2/topology
```

Repeat until the response contains `"health":"healthy"` — normally ~25 seconds. **Do not start the
app before this**, or the client fails to connect.

```json
{"brokers":[{"nodeId":0,...,"partitions":[{"partitionId":1,"role":"leader","health":"healthy"}]...
```

### 4 — Run the application

```powershell
mvn spring-boot:run -pl service-template "-Dspring-boot.run.profiles=localdocker"
```

> `-pl` is lowercase **P**, lowercase **L** ("projects list"), followed by a **space**. Typing
> `-p1service-template` gives `Unrecognized option: -p1service-template`.
>
> PowerShell needs the `-D` argument in quotes. Bash does not.

Expected — these three lines confirm success:

```
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema "PUBLIC", now at version v1
o.s.boot.tomcat.TomcatWebServer     : Tomcat started on port 8081 (http) with context path '/'
c.a.c.service.template.Application  : Started Application in 12.04 seconds (process running for 13.456)
```

The terminal now **stays blocked** — that is correct. The app is running in the foreground.

---

## Verify it is running

Open a **second** terminal (the first is busy running the app).

### The check that matters

```powershell
curl.exe -s http://localhost:8081/actuator/health
```

| Response | Meaning |
|---|---|
| `{"groups":["liveness","readiness"],"status":"UP"}` | ✅ Running correctly |
| `{"status":"DOWN", ...}` | Started, but a contributor is failing — usually the Camunda client |
| nothing, exit code **7** | Not running (connection refused) |
| nothing, exit code **28** | Still starting — retry for ~30s |

> In PowerShell use **`curl.exe`**, not `curl`. Plain `curl` is an alias for `Invoke-WebRequest`
> and takes completely different arguments.

### The rest

```powershell
# liveness / readiness separately
curl.exe -s http://localhost:8081/actuator/health/liveness
curl.exe -s http://localhost:8081/actuator/health/readiness

# what is holding the port (should be java.exe)
netstat -ano | findstr :8081

# the engine
curl.exe -s http://localhost:8080/v2/topology

# containers
docker ps
```

### Two things that look broken but are correct

**1. No workflow activity in the log, ever.** The scaffold ships no `@JobWorker` and no BPMN, so
there are no jobs to pull. Silence is the designed state of an empty scaffold — it does *not* mean
the cluster connection is broken.

**2. `/actuator/prometheus` returns 404.** `service-template` does not depend on
`framework-observability-starter`, so no Prometheus registry exists. Add that starter if you want
metrics. (`application.yml` advertises the endpoint and the Helm chart scrapes it — a real
inconsistency, but harmless locally.)

### There is no app container — and there shouldn't be

A common point of confusion. These are **alternatives**; you only need one:

| | Where the app runs | Port | How you start it |
|---|---|---|---|
| **Maven** (these instructions) | Java process on Windows | **8081** | `mvn spring-boot:run` |
| Container | Docker container `svc-tmpl` | 8082 | `docker run ... service-template:local` |

Running via Maven means **`docker ps` shows only `camunda-local`**. That is correct. The engine is
in Docker; your app is not.

---

## Stopping

```powershell
# app      — Ctrl+C in the terminal running Maven
docker stop camunda-local          # engine
```

If `Ctrl+C` leaves the port held (it occasionally does on Windows):

```powershell
netstat -ano | findstr :8081       # note the PID in the last column
taskkill /PID <pid> /F
```

Full cleanup, if you want the machine back to nothing:

```powershell
docker rm camunda-local            # deletes the container (config is lost; re-run docker run)
docker image rm service-template:local
# keep camunda/camunda:8.9.0 — mvn verify needs it, and it is a 1.27 GB re-download
```

---

## Three ways to run it

### Option A — self-managed engine in Docker ⭐ recommended

What this runbook describes. **No cloud account, no credentials.** App on **8081**.

```powershell
docker start camunda-local
mvn spring-boot:run -pl service-template "-Dspring-boot.run.profiles=localdocker"
```

### Option B — Camunda 8.9 SaaS

The way the scaffold ships. Needs a cluster at [console.camunda.io](https://console.camunda.io).
App on **8080**.

```powershell
# 1. copy .env.example -> .env and fill in the four values
#      CAMUNDA_CLIENT_ID, CAMUNDA_CLIENT_SECRET, CAMUNDA_CLUSTER_ID, CAMUNDA_REGION

# 2. load them into THIS shell (they do not cross terminal windows)
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2])
    }
}

# 3. run
mvn spring-boot:run -pl service-template "-Dspring-boot.run.profiles=local"
```

If credentials are wrong the process **exits immediately with code 1** — it does not start and fail
later. So a process that stays up has already authenticated successfully.

### Option C — the container image

Validates the artifact CI would ship. App on **8082**.

```powershell
docker build -f service-template/Dockerfile -t service-template:local .

docker run -d --name svc-tmpl `
  -e SPRING_PROFILES_ACTIVE=local `
  -e CAMUNDA_CLIENT_MODE=self-managed `
  -e CAMUNDA_CLIENT_AUTH_METHOD=none `
  -e CAMUNDA_CLIENT_RESTADDRESS=http://host.docker.internal:8080 `
  -e CAMUNDA_CLIENT_GRPCADDRESS=http://host.docker.internal:26500 `
  -p 8082:8080 service-template:local

docker logs -f svc-tmpl
curl.exe -s http://localhost:8082/actuator/health
```

Verified: image 657 MB, runs as `uid=1000(spring)` (matching the Helm chart's `runAsUser`), Flyway
applies, `HEALTHCHECK` reports `healthy` at ~45s.

`CAMUNDA_CLIENT_AUTH_METHOD=none` is required — the `local` profile inherits `mode=saas`, so
pointing it at a local engine means overriding both the mode **and** the auth method.

---

## Port map

| Port | Used by | Notes |
|---|---|---|
| 8080 | Camunda engine (REST) | also SaaS-mode app port under Option B |
| 26500 | Camunda gRPC | |
| **8081** | **your app under Option A** | engine has 8080, so the app moves up |
| 8082 | your app under Option C | host-side mapping of the container's 8080 |

---

## Troubleshooting

Real errors encountered while getting this working.

| Error | Cause | Fix |
|---|---|---|
| `Unrecognized option: -p1service-template` | Typo — digit `1` instead of letter `l`, and no space | `-pl service-template` |
| `Cannot load driver class: org.h2.Driver` | H2 was `<scope>test</scope>` | Already fixed (`runtime`). If you restore the original pom, re-apply |
| App starts but **no** `Successfully applied` line | `spring-boot-flyway` missing → Flyway silently never runs → framework tables never created | Already fixed. This one is dangerous — it is completely silent |
| `Syntax error ... WHERE dispatched_at IS NULL` | Partial index is Postgres-only, fails on H2 | Already fixed in `V1__framework_tables.sql` |
| `Child module /workspace/framework-web-starter ... does not exist` | Dockerfile did not copy that module | Already fixed |
| `groupadd: GID '1000' already exists` | Base image ships an `ubuntu` account at UID 1000 | Already fixed (`userdel -r ubuntu` first) |
| `Failed to retrieve well known configuration` / `Connection refused`, exit 1 | Engine not reachable, or SaaS creds wrong/missing | Wait for `"health":"healthy"` before starting the app; check the four `CAMUNDA_*` values |
| `Port 8080 is already in use` | Engine holds it | Use the `localdocker` profile (8081), or `"-Dserver.port=8081"` |
| `curl : ... Invoke-WebRequest` parameter errors | PowerShell aliases `curl` | Use `curl.exe` |
| `mvn verify` fails at `framework-test` | Docker not running | Start Docker Desktop, or skip that test class |
| Container stuck `unhealthy` past 60s | Liveness failing inside | `docker exec svc-tmpl curl -f http://localhost:8080/actuator/health/liveness` |

---

## What changed from the original

Six defects stopped the original from running. All were reproduced from a clean state and fixed.
Full write-up with reproductions: `FRAMEWORK-GUIDE.md` §15.9.

### Code — 4 files

| File | Change | Why |
|---|---|---|
| `service-template/pom.xml` | H2 `test` → `runtime` | The `local` profile uses H2; at test scope the driver is absent from the runtime classpath **and the fat jar** |
| `service-template/pom.xml` | added `spring-boot-flyway` | Boot 4 split auto-config into per-technology modules. Without it `spring.flyway.enabled=true` is inert — **no migration, no error, no log line**, and `worker_execution` / `process_outbox` never exist |
| `db/migration/V1__framework_tables.sql` | index → `(dispatched_at, created_at)` | `CREATE INDEX ... WHERE` (partial index) is Postgres-only and is a syntax error on H2 |
| `service-template/Dockerfile` | added 2 `COPY` lines for `framework-web-starter` | It is a declared reactor module; Maven aborts without it |
| `service-template/Dockerfile` | `userdel -r ubuntu` before `groupadd` | Base image owns UID 1000. UID must stay 1000 — Helm pins `runAsUser: 1000` |
| `application-localdocker.yml` | **new file** | Run against a local engine instead of SaaS |

**Not fixed** — a decision for the team: `/actuator/prometheus` 404s because `service-template`
does not depend on `framework-observability-starter`, even though `application.yml` advertises the
endpoint and the Helm `ServiceMonitor` scrapes it.

### Documentation

| File | Change |
|---|---|
| `README.md` | added verification + no-cloud-account sections; corrected the Docker and JaCoCo claims |
| `CLAUDE.md` | added the defect table and corrected build commands |
| `FRAMEWORK-GUIDE.md` | **new** — full reference, 15 sections + 3 appendices |
| `docs/…guide.docx` | **new** — Word version |
| `HOW-TO-RUN.md` | **new** — this file |

**Untouched:** all 7 framework modules (no Java logic changed), the parent `pom.xml`,
`.gitlab-ci.yml`, the Helm chart, `tools/`, `DEPLOYMENT-LOCAL.md`, `UNDERSTANDING.md`.

### Known documentation errors in the original

Worth raising with whoever maintains the docs:

- **`README.md`** — claims JaCoCo coverage reports are produced. They are not; JaCoCo never runs.
- **`README.md`** — lists Docker as optional. It is required for `mvn verify`.
- **`DEPLOYMENT-LOCAL.md`** — all three run paths fail against the unmodified code.
- **`DEPLOYMENT-LOCAL.md`** — states missing credentials "fail late at the first Camunda call, not
  at startup". The opposite is true: the context fails to initialise and the process exits 1.
- **`DEPLOYMENT-LOCAL.md`** — references `PROGRESS.md`, which does not exist in the repo.
- Verified as **correct**: the "183 tests, 0 failures" claim.

---

## What to do next

The framework is proven working end to end, but it is a *scaffold* — it deliberately contains no
business logic. To make it do something, add to `service-template`:

1. **A BPMN file** → `src/main/resources/processes/`
2. **A worker** extending `BaseWorker<V>` → `infrastructure/camunda/`
   — that package specifically; ArchUnit rule 2 rejects `io.camunda.client` imports anywhere else
3. **`@JobWorker(type = "...")`** matching the BPMN `<zeebe:taskDefinition type="...">` **exactly**
4. **An ArchUnit test** wiring up the six rules from `framework-test` — without it you get zero
   architectural enforcement and no warning

Then you will see job activation in the log. Walkthrough: `FRAMEWORK-GUIDE.md` §14 (scaffolding)
and §6 (how `BaseWorker` executes a job).
