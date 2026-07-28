# incident-response-service — Assessment Use Case 4

Cyber Security Incident Response on **Camunda 8.9**, built on **camunda-process-framework**
(modeled on `order-service-sample`). Backend = idempotent Spring Boot 4 job workers; human tasks
are completed in **Camunda Tasklist**.

Full requirements & design: `../usecase-4/UC4-REQUIREMENTS.md`.

## Architecture (hexagonal — enforced by ArchUnit)
```
web/                 IncidentController + DTOs (raise / read incident)
application/          IncidentService (starts process, worker callbacks, @Transactional)
domain/              Incident (AuditableEntity state machine), IncidentStatus, IncidentSeverity
repository/          IncidentRepository
infrastructure/camunda/          CamundaDeploymentConfig (deploys BPMN + DMN)
infrastructure/camunda/worker/   12 BaseWorker job workers (idempotent, businessKey = incidentId)
config/              OpenApiConfig (Swagger)
resources/processes/incident-response.bpmn
resources/dmn/incident-classification.dmn
resources/db/migration/  V1 (framework tables) + V2 (incidents)
```

## Process flow (`incident-response`)
`SIEM start → Triage (worker*) → Classify (DMN business rule task → severity) → Record Classification
(worker) → P4? gateway ── P4 → Auto Close → end ── else → parallel [Containment: Isolate (worker,
error ISOLATION_FAILED → Incident Commander) → Containment Verification (SOC); Forensics: Collect
Evidence (worker) → Forensic Analysis (Forensics Lead); Notify Stakeholders (worker)] → join → CISO
Review (CISO) → Restore Services (worker) → Integrity Verification (SOC) → Post-Incident Report
(worker*) → Incident Closure (Incident Commander) → Close Incident (worker) → end`
(*Triage and Post-Incident Report are runnable placeholders for Camunda AI connector steps.)

Job worker types → classes: `triage-threat`, `record-classification`, `auto-close`,
`isolate-systems`, `collect-evidence`, `notify-stakeholders`, `restore-services`, `close-incident`,
`generate-report`, and the ad-hoc actions `block-ip`, `revoke-credentials`, `deploy-patch`.

## Run
Prereqs: Java 21, Maven, framework installed to `~/.m2` (`mvn install -DskipTests` in the framework
repo), **PostgreSQL 17 running** with the `incident_response` DB/role (see
`application-local.yml.example`), and Camunda 8.9 SaaS creds in `application-local.yml`.
```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"     # quote the -D arg in PowerShell
```
Expect: `Successfully applied 2 migrations`, `Deployed Processes: <incident-response:1>`,
`Deployed Decisions: <incident-classification:1>`, 12 workers registered.

## Test
```powershell
curl -X POST http://localhost:8080/incidents -H "Content-Type: application/json" -d '{\"title\":\"Lateral movement on prod-db\",\"source\":\"SIEM\"}'
curl http://localhost:8080/incidents/<id>     # severity=P1, status flips RAISED->TRIAGED->CLASSIFIED
# force the isolation-failure escalation path:
curl -X POST http://localhost:8080/incidents -H "Content-Type: application/json" -d '{\"title\":\"...\",\"source\":\"SIEM\",\"forceIsolationFailure\":true}'
```
Human tasks (Containment Verification, Forensic Analysis, CISO Review, closure, ...) are then
completed in **Tasklist**. Swagger: `http://localhost:8080/swagger-ui/index.html`.

## Status
**Implemented & verified end-to-end:**
- Project scaffold on the framework; incident domain + state machine; **13** idempotent job workers.
- **Two DMN tables** — Incident Classification (P1–P4) and Regulatory Notification Required — both
  deployed and invoked via business rule tasks (`zeebe:calledDecision`).
- The `incident-response` **BPMN**: parallel containment/forensics/notification, **ad-hoc
  response-actions sub-process** (commander activates block-ip / revoke-credentials / deploy-patch
  at runtime via `activeElementsCollection`), isolation-failure **BPMN-error escalation** to the
  commander, **P4 auto-close**, native user tasks with candidate groups per persona.
- **Timers & escalation (UC4 core):** severity-based **SLA timer** on CISO Review (non-interrupting,
  duration `=slaDuration` derived from severity) → escalate; **72-hour** regulatory-deadline timer
  (non-interrupting) on the Legal notification task → escalate to CISO.
- **Tasklist forms** (`src/main/resources/forms/*.form`) for every human task, linked via
  `zeebe:formDefinition` and deployed with the process — containment verification, forensic
  analysis, CISO review, integrity verification, regulatory notification, isolation-failure
  handling, and closure.
- **BPMN DI** (diagram layout) generated with Camunda's `bpmn-auto-layout`, so Operate renders the
  diagram.
- Postgres; Swagger; ArchUnit (6/6 pass). Raising an incident runs triage → DMN P1 → classified →
  all four parallel branches (incl. ad-hoc) with no incidents, then waits at the human tasks (which
  now show forms in Tasklist).

- **AI connector steps** — threat triage (`Task_TriageAI`) and post-incident report
  (`Task_ReportAI`) are **OpenAI** calls via the HTTP REST connector (`io.camunda:http-json:1`):
  prompts built from process variables, result mapped to `triageReport` / `postIncidentReport`,
  retries, and an **error boundary that falls back to the equivalent job worker** so the flow always
  completes. Auth uses the `{{secrets.OPENAI_API_TOKEN}}` cluster secret. Verified: an incident runs
  through the AI triage step (or its fallback) to CLASSIFIED with no incidents.

### AI connector setup (to make the AI steps actually call OpenAI)
1. In **Camunda Console → your cluster → Connector secrets**, add `OPENAI_API_TOKEN` = your OpenAI
   key (from the git-ignored `*.env`).
2. Without the secret, the AI tasks error → the boundary catches it → the fallback worker runs and
   the flow still completes. With it, the AI produces real triage / report text.
3. Swap for the dedicated **OpenAI** or **AI Agent** connector template in Web Modeler if preferred
   (same secret). The `.env` also has **Google Vertex AI** + **SendGrid** keys for alternative
   providers / a real notification connector.

- **Human-task API + outcome persistence** — complete Tasklist user tasks from the API and record
  each outcome to Postgres (`incident_task_outcomes`):
  ```
  GET  /incidents/{id}/tasks                       active user tasks (key, elementId, name)
  POST /incidents/{id}/tasks/{userTaskKey}/complete  {"completedBy":"...","variables":{...}}
  POST /incidents/{id}/tasks/complete              complete the single active task
  GET  /incidents/{id}/tasks/outcomes              recorded outcomes (from Postgres)
  ```
  The incident stores its `process_instance_key`; `CamundaTaskAdapter` searches/completes user tasks
  via the v2 API. Verified: raise → complete Containment Verification/Forensic Analysis via API →
  outcome row in `incident_task_outcomes`. (The graded demo can still complete tasks in Tasklist.)

### Database (updated)
Now runs as **`postgres` / `postgres`** against a fresh `incident_response` database (V1 framework
tables, V2 `incidents`, V3 `incident_task_outcomes` + `incidents.process_instance_key`). Create it:
`CREATE DATABASE incident_response OWNER postgres;` (superuser postgres/postgres).

- **Embedded sub-processes** — Containment, Forensics, and Recovery are modeled as embedded
  sub-processes (the isolation-failure error boundary lives inside the Containment sub-process).
  Verified end-to-end: an incident was driven all the way to **CLOSED** via the task API (Containment
  Verification + Forensic Analysis → CISO Review → Integrity Verification → File Regulatory
  Notification → Incident Closure), with all six outcomes persisted to Postgres.

**Optional polish:**
- A `completing` task listener so Tasklist-channel completions also record outcomes.
- Tune SLA/timer durations, AI prompts, and enrich the form fields.

> Regenerate DI after editing the BPMN: run `bpmn-auto-layout` (the `layout.mjs` script used during
> setup), or re-open in Web Modeler. NEVER commit the `*.env` (it holds live API keys — rotate them).
