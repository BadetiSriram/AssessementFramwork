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
- Postgres; Swagger; ArchUnit (6/6 pass). Raising an incident runs triage → DMN P1 → classified →
  all four parallel branches (incl. ad-hoc) with no incidents, then waits at the human tasks.

**Next iterations (best done in Web Modeler):**
- Replace the Triage and Post-Incident-Report workers with **AI connector** tasks.
- Wrap Containment / Forensics / Recovery in **embedded sub-processes**.
- Add BPMN **DI** (diagram layout) so Operate renders it and it's presentable for the demo.
- Tune SLA/timer durations and add Tasklist **forms** for each human task.
