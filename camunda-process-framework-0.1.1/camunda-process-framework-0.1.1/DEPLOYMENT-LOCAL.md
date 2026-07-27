# DEPLOYMENT-LOCAL.md

Local development deployment guide for `camunda-process-framework` — how to build the
multi-module project, configure credentials, and run the `service-template` scaffold on
your workstation.

## Scope

This guide covers **three local run paths**, all against a real Camunda 8.9 SaaS cluster:

- **Path A** — `local` profile, H2 in-memory database, run via `mvn spring-boot:run`.
  Fastest path; no external services except Camunda SaaS.
- **Path B** — `dev` profile, PostgreSQL 16 in Docker, run via `mvn spring-boot:run`.
  Prod-shaped datasource, verbose logs, all actuator endpoints exposed.
- **Path C** — build the `service-template` Docker image and run it as a container.
  Validates the runtime artifact you would ship.

Plus **IDE workflows** for IntelliJ IDEA and Eclipse — equivalent to Path A or B but
launched from a run configuration inside the IDE so you can debug, hot-reload, and set
breakpoints.

Not covered here: Kubernetes / Helm deployment (see `deploy/helm-chart/README.md`),
GitLab CI pipeline (see `.gitlab-ci.yml`), or scaffolding a new service from
`service-template/` (see the root `README.md`).

**Note on "all services".** This repo produces one runnable Spring Boot application —
`service-template`. The other seven modules (`framework-core`,
`framework-camunda-starter`, `framework-security-starter`,
`framework-observability-starter`, `framework-data-starter`, `framework-test`,
`framework-web-starter`) are libraries with no `main()` method. You exercise them via
their unit tests, not by "running" them.

## Prerequisites

| Tool | Version | Verify | Required for |
|---|---|---|---|
| Java (JDK) | 21.x | `java -version` | all paths |
| Maven | 3.9+ | `mvn -v` | Paths A, B (and building the image in C) |
| Docker | 24+ | `docker version` | Paths B and C |
| Camunda 8.9 SaaS cluster | — | see below | all paths |
| `curl` or a browser | — | `curl --version` | verifying `/actuator/health` |

**Camunda SaaS credentials.** Sign in at [console.camunda.io](https://console.camunda.io),
create (or open) a cluster on the 8.9 line, then create an API client with
`Zeebe` scope. You need four values:

- `CAMUNDA_CLIENT_ID`
- `CAMUNDA_CLIENT_SECRET`
- `CAMUNDA_CLUSTER_ID` — the UUID visible in the cluster URL
- `CAMUNDA_REGION` — e.g. `bru-2`, `gcp-us-central1`

**Windows note.** Commands are shown for both Bash (Git Bash / WSL) and PowerShell where
they differ. If you use PowerShell, use the PowerShell block — `set -a && source .env`
silently no-ops there.

## Step 1 — Configure environment

Copy the credential template and fill in your cluster values. `.env` is gitignored
(`.gitignore:32`); never commit it.

```bash
cp .env.example .env
# then edit .env
```

`.env` after editing:

```
CAMUNDA_CLIENT_ID=<oauth client id>
CAMUNDA_CLIENT_SECRET=<oauth client secret>
CAMUNDA_CLUSTER_ID=<cluster uuid>
CAMUNDA_REGION=<region, e.g. bru-2>
```

Load the variables into your current shell.

**Bash / Git Bash / WSL:**

```bash
set -a && source .env && set +a
```

**PowerShell:**

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2])
    }
}
```

Verify with `echo $CAMUNDA_CLUSTER_ID` (Bash) or `$env:CAMUNDA_CLUSTER_ID` (PowerShell).

## Step 2 — Build

From the repository root:

```bash
mvn verify
```

Expected outcome: `BUILD SUCCESS` across eight modules, **183 tests passing, 0 failures**.
Artifacts produced:

- Jars for the six library modules (`framework-*/target/*.jar`)
- Fat, layered Boot jar at `service-template/target/service-template-0.1.0-SNAPSHOT.jar`
- JaCoCo coverage reports under each module's `target/site/jacoco/`

If Maven Central is unreachable and dependency resolution fails, the build cannot be
verified locally; see the "Troubleshooting" section.

## Step 3 — Choose a run path

| Path | When to use | External services |
|---|---|---|
| A | Everyday local dev, quick iteration | Camunda SaaS only |
| B | Reproducing Postgres-specific behaviour (`FOR UPDATE SKIP LOCKED`, `jsonb`) | Camunda SaaS + Postgres in Docker |
| C | Validating the shipped container image (what CI builds) | Camunda SaaS (+ Docker) |

### Path A — H2 in-memory via Maven

The `local` profile (`application-local.yml`) overrides the datasource to H2 in
`MODE=PostgreSQL`. Fastest path — nothing to install.

```bash
mvn spring-boot:run -pl service-template -Dspring-boot.run.profiles=local
```

PowerShell needs the `-D` argument quoted:

```powershell
mvn spring-boot:run -pl service-template "-Dspring-boot.run.profiles=local"
```

Skip to **Verify the deployment** below.

### Path B — Postgres 16 in Docker via Maven

The `dev` profile (`application-dev.yml`) leaves the datasource untouched, so the
service uses the defaults from `application.yml`:
`jdbc:postgresql://localhost:5432/service_template`, user `service_template`.
Start a matching Postgres container:

```bash
docker run --name svc-tmpl-pg \
    -e POSTGRES_DB=service_template \
    -e POSTGRES_USER=service_template \
    -e POSTGRES_PASSWORD=service_template \
    -p 5432:5432 \
    -d postgres:16
```

Export the password so Spring picks it up (the datasource username is already the
default `service_template`, so only the password needs to be set):

```bash
export DB_PASSWORD=service_template          # Bash
```

```powershell
$env:DB_PASSWORD = "service_template"        # PowerShell
```

Start the service with the `dev` profile:

```bash
mvn spring-boot:run -pl service-template -Dspring-boot.run.profiles=dev
```

Flyway will create `worker_execution` and `process_outbox` on first startup.

### Path C — Docker container of `service-template`

Build the image from the repo root (the Dockerfile expects this context because it
copies each module's POM independently for cache layering):

```bash
docker build -f service-template/Dockerfile -t service-template:local .
```

Expected: multi-stage build completes with the runtime stage tagged
`service-template:local`. First build takes a few minutes (populates the Maven cache
layer); subsequent code-only rebuilds reuse everything except the `application` layer.

Run the container, passing credentials via `--env-file` and choosing a profile via
`SPRING_PROFILES_ACTIVE`:

```bash
docker run --rm \
    --name service-template \
    --env-file .env \
    -e SPRING_PROFILES_ACTIVE=local \
    -p 8080:8080 \
    service-template:local
```

If you want the container to reach the Postgres from Path B on Linux/macOS, add
`--network host`. On Docker Desktop for Windows/macOS, replace `localhost` in
`DB_URL` with `host.docker.internal`:

```bash
docker run --rm \
    --name service-template \
    --env-file .env \
    -e SPRING_PROFILES_ACTIVE=dev \
    -e DB_URL=jdbc:postgresql://host.docker.internal:5432/service_template \
    -e DB_PASSWORD=service_template \
    -p 8080:8080 \
    service-template:local
```

The container's `HEALTHCHECK` polls `/actuator/health/liveness` every 30s — inspect
its state with `docker inspect --format='{{json .State.Health}}' service-template`.

## Run from an IDE (IntelliJ IDEA or Eclipse)

Equivalent to Path A (H2) or Path B (Postgres), launched from an IDE run
configuration so you can debug and set breakpoints. Complete **Step 1 (env vars)** and
**Step 2 (`mvn verify`)** first — the `mvn verify` run populates snapshot jars for the
seven library modules so IDE navigation, indexing, and inter-module compilation resolve
correctly.

**Common facts for both IDEs.** The main class is
`com.aaseya.camunda.service.template.Application`; the run module is `service-template`;
the JDK is 21; the four `CAMUNDA_*` env vars are mandatory (missing values fail late at
the first Camunda call, not at startup, so a run config that skips them can *look* fine
until it isn't). Neither IDE reads `.env` files natively — paste the vars into the run
config or install an env-file plugin.

### IntelliJ IDEA

**One-time setup**

1. **File > Open**, select the repo-root `pom.xml`, choose **Open as Project**. All 8
   modules import automatically.
2. **File > Project Structure > Project** — set **SDK: 21** and **Language level: 21**.
3. Open the **Maven** tool window and verify all 8 modules are listed. Click refresh if
   any are missing.

**Create the run configuration**

1. **Run > Edit Configurations > + > Spring Boot** (Ultimate) *or* **Application**
   (Community — no functional loss for this project).
2. Fill in:
   - **Name:** `service-template [local]`
   - **Main class:** `com.aaseya.camunda.service.template.Application`
   - **Module (Use classpath of module):** `service-template`
   - **JRE:** `21`
   - **Active profiles** (Spring Boot config type only): `local` — or use VM options:
     `-Dspring.profiles.active=local`
   - **Environment variables:** semicolon-separated —
     `CAMUNDA_CLIENT_ID=...;CAMUNDA_CLIENT_SECRET=...;CAMUNDA_CLUSTER_ID=...;CAMUNDA_REGION=...`.
     Alternatively install the **EnvFile** plugin, tick **Enable EnvFile**, and add
     `.env`.
3. **Apply**, then **Run** (Shift+F10) or **Debug** (Shift+F9).

**For Path B (Postgres):** duplicate the config (right-click > **Copy Configuration**),
rename to `service-template [dev]`, change the profile to `dev`, add `DB_PASSWORD` to
the environment variables, and start the Postgres container from Path B before running.

**Run tests from any framework module.** Right-click a test class or the module in the
Project view → **Run 'Tests in <module>'**. Use **Run with Coverage** to view JaCoCo
data inline.

### Eclipse

**One-time setup**

1. Install **Eclipse IDE for Enterprise Java and Web Developers** (bundles m2e).
   Recommended: also install **Spring Tools 4** from the Eclipse Marketplace for the
   **Boot Dashboard** and the **Spring Boot App** run type.
2. **File > Import > Maven > Existing Maven Projects**, root = repo root, select all 8
   modules, **Finish**. Wait for the import job to complete.
3. **Window > Preferences > Java > Installed JREs** → add JDK 21 and mark it default.
   **Java > Compiler** → set **Compliance level: 21**.
4. Right-click the parent project → **Maven > Update Project** (Alt+F5), tick all
   modules. Repeat any time a `pom.xml` changes.

**Create the run configuration**

1. **Run > Run Configurations... > Spring Boot App** (if STS is installed) *or*
   **Java Application** (without STS).
2. **Main** tab:
   - **Project:** `service-template`
   - **Main class:** `com.aaseya.camunda.service.template.Application`
3. **Arguments** tab → **VM arguments:** `-Dspring.profiles.active=local`
4. **Environment** tab → click **New...** for each of the four `CAMUNDA_*` variables and
   add its value. For Path B, add `DB_PASSWORD=service_template` as well. Eclipse's run
   config has no `.env`-file loader; the Environment tab is the only path.
5. **JRE** tab: select JDK 21.
6. **Apply**, then **Run** or **Debug**.

**Boot Dashboard shortcut (STS only).** After the run config exists, `service-template`
appears in the **Boot Dashboard** view — start/stop/restart with one click, and the
dashboard surfaces the live actuator endpoints.

**Run tests from any module.** Right-click a test class or `src/test/java` →
**Run As > JUnit Test**. Coverage: **Run As > Coverage As > JUnit Test** (requires
EclEmma, bundled in the Enterprise edition).

### High-value breakpoints

Once you add a `@JobWorker`, the highest-leverage breakpoint is
`BaseWorker.execute()` in `framework-core` — every job activation flows through it, so
it's the single best place to observe variable deserialisation, idempotency check,
error classification, and Micrometer counter emission in one step-through.

## Verify the deployment

Regardless of which path you took, all of the following should be true within ~30 seconds
of startup:

1. **Health endpoint is UP.**

   ```bash
   curl -s http://localhost:8080/actuator/health
   ```
   Expected: `{"status":"UP"}` (plus component details on `dev` profile, which exposes
   `management.endpoints.web.exposure.include: "*"`).

2. **Camunda cluster connected.** In the application log, look for successful startup
   of the Camunda Spring auto-configuration and no `UNAUTHENTICATED` or `UNAVAILABLE`
   errors from the gRPC/REST client. A misconfigured `CAMUNDA_REGION` typically surfaces
   here as a DNS or 404 error.

3. **Flyway migrations applied.**
   - Path A (H2): the log shows `Successfully applied 1 migration to schema "PUBLIC"`.
     There is no external H2 console wired up.
   - Path B (Postgres): connect and inspect:
     ```bash
     docker exec -it svc-tmpl-pg psql -U service_template -d service_template \
         -c '\dt'
     ```
     Expected tables: `flyway_schema_history`, `worker_execution`, `process_outbox`.

4. **No workers pull jobs.** The scaffold ships no `@JobWorker` classes. Absence of job
   activation is expected — it does not indicate a broken cluster connection. Add a
   worker (extending `BaseWorker<V>`) to see activity.

## Stopping and cleaning up

- **Maven-run process** (Paths A, B): stop with `Ctrl+C` in the terminal.
- **Container** (Path C): stop with `docker stop service-template` (it auto-removes
  because of `--rm`).
- **Postgres container** (Path B): `docker stop svc-tmpl-pg && docker rm svc-tmpl-pg`.
  This also deletes the H2/Postgres data; on the next start Flyway re-applies migrations
  from scratch.
- **Image cleanup**: `docker image rm service-template:local` when done.

## Troubleshooting — local-dev specifics

- **`.env` values not visible to the application.** The variable expansions in
  `application.yml` (`${CAMUNDA_CLIENT_ID:}`) fall back to an empty string when the
  env var is missing, and startup then fails at the first Camunda call rather than at
  bind time. Re-run the shell loader from Step 1 in the same terminal that runs Maven
  or Docker — env vars do not cross terminal windows.

- **`Cannot connect to the Docker daemon`.** Docker Desktop is not running, or your
  user is not in the `docker` group on Linux. Fix before retrying Paths B or C.

- **`Port 8080 is already in use`.** Another process is bound. Either free the port, or
  override with `-Dserver.port=8081` (Maven) or `-e SERVER_PORT=8081 -p 8081:8081` (Docker).

- **`Port 5432 is already in use`.** A native Postgres install is running. Either stop
  it, or map the container to another host port (`-p 5433:5432`) and adjust `DB_URL`
  to `jdbc:postgresql://localhost:5433/service_template`.

- **Flyway `Validate failed: Migration checksum mismatch`.** You edited
  `V1__framework_tables.sql` after it had been applied to a persistent database. For
  local dev, drop and recreate the Postgres container (`docker rm -f svc-tmpl-pg` then
  re-run the `docker run` from Path B). Never edit an already-applied migration in
  shared environments.

- **`mvn verify` fails at dependency resolution.** Maven Central is unreachable from
  your network. Run just the offline checks:
  ```bash
  find . -name "*.bpmn" -o -name "*.dmn" | xargs -r xmllint --noout
  ./tools/check-bpmn-integrity.sh
  ```
  Note in your task report that the build is Docker-deferred; do not claim tests pass
  without a successful `mvn verify` run.

- **`NoSuchBeanDefinitionException: ObjectMapper`.** The framework provides a Jackson
  2.x `ObjectMapper` bean via `@ConditionalOnMissingBean`. If your consuming code
  provides its own Jackson 3.x `JsonMapper` bean, the framework's bean backs off and
  `VariableMapper` / `OutboxRelay` fail to wire. Inject the Jackson 2.x
  `com.fasterxml.jackson.databind.ObjectMapper` type explicitly.

- **Container health check stays `starting` past 60s.** The `HEALTHCHECK` grants a
  60-second `--start-period` for JVM warm-up. If the container never reaches `healthy`,
  inspect the liveness endpoint from inside the container:
  `docker exec service-template curl -f http://localhost:8080/actuator/health/liveness`.

## References

- Root `README.md` — full project overview, module descriptions, configuration reference,
  cardinal invariants.
- `service-template/README.md` — how to fork the scaffold into a new service.
- `deploy/helm-chart/README.md` — Kubernetes / Helm deployment.
- `PROGRESS.md` — build history, verified pin decisions, and known limitations.
- `CLAUDE.md` — the build-agent operating loop (relevant if you are extending the
  framework, not just running it).
