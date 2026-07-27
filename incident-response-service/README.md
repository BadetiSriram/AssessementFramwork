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

## Status (as of the first build)
**Implemented & verified end-to-end:** project scaffold on the framework; incident domain +
state machine; all 12 idempotent job workers; Incident Classification **DMN** (deployed, invoked
via a business rule task); the `incident-response` **BPMN** (parallel containment/forensics/
notification, isolation-failure BPMN-error escalation, P4 auto-close, native user tasks with
candidate groups per persona); Postgres; Swagger; ArchUnit (6/6 pass). Raising an incident runs the
automated chain through DMN classification to the human tasks.

**Next iterations (best done in Web Modeler):**
- Replace the Triage and Post-Incident-Report workers with **AI connector** tasks.
- Add the **ad-hoc response-actions** sub-process (block-ip / revoke-credentials / deploy-patch —
  workers already exist).
- Add **timers & escalation** (72h regulatory deadline → CISO; severity-based SLA timers on human
  tasks) — UC4's core capability.
- Add the **Regulatory Notification Required** DMN + Legal notification path.
- Wrap Containment / Forensics / Recovery in **embedded sub-processes**; add BPMN **DI** (diagram
  layout) so Operate renders it and it's presentable.
