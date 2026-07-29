# Run and process walkthrough

How to start the backend, then every step of `incident-response` from SIEM alert to CLOSED: which
worker runs, what it does, and what it writes.

---

## A. Running it

You need three things in place first:

1. PostgreSQL 17, with `CREATE DATABASE incident_response OWNER postgres;` (login postgres/postgres).
2. The framework installed to `~/.m2` - `mvn install -DskipTests` in the framework repo, once, and
   again after any framework change.
3. `application-local.yml` with your Camunda 8.9 SaaS credentials (cluster id, region, client id,
   client secret). It's git-ignored; copy `application-local.yml.example`.

**Start it**
```powershell
cd C:\Users\Sriram.Badeti\Desktop\Assessment\incident-response-service
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

**What you should see in the log, in order**

1. Hikari connects to Postgres.
2. Flyway: `Successfully applied 3 migrations` (framework tables, `incidents`,
   `incident_task_outcomes`).
3. Hibernate validates the mappings against those tables - `ddl-auto` is `validate`, so a mismatch
   fails startup rather than quietly altering the schema.
4. Deployment: the process, both decisions, and 7 forms.
5. 13 workers register, one line each: `triage-threat`, `record-classification`, `auto-close`,
   `isolate-systems`, `collect-evidence`, `notify-stakeholders`, `restore-services`,
   `close-incident`, `generate-report`, `block-ip`, `revoke-credentials`, `deploy-patch`,
   `escalate`.
6. `Started IncidentResponseApplication` on port 8080. Swagger at `/swagger-ui/index.html`.

**Trigger a process**
```powershell
# Default -> classifies P1 (full response, waits at the human tasks)
curl -X POST http://localhost:8080/incidents -H "Content-Type: application/json" -d "{\"title\":\"Ransomware on prod-db\",\"source\":\"SIEM\"}"

# Request-driven DMN variants (steer the classification):
#   P4 false positive -> auto-closes, no human tasks
curl -X POST http://localhost:8080/incidents -H "Content-Type: application/json" -d "{\"title\":\"Benign alert\",\"source\":\"SIEM\",\"attackConfirmed\":false}"
#   P2 contained, no data loss -> regulatory branch skipped
curl -X POST http://localhost:8080/incidents -H "Content-Type: application/json" -d "{\"title\":\"Contained breach\",\"source\":\"SIEM\",\"assetCriticality\":\"HIGH\",\"dataExposed\":false}"
```

---

## B. The process, step by step

### Start: `POST /incidents`

`IncidentController` hands off to `IncidentService.raiseIncident()`, which creates the aggregate at
status RAISED, saves it, and starts the process. The returned process instance key gets stored on
the incident, which is how we find its user tasks later.

Variables handed to Camunda at start: `businessKey` (the incident id), `incidentId`, `title`,
`source`, `forceIsolationFailure`, an empty `triageReport`, `responseActions`
(`["Task_BlockIp","Task_RevokeCredentials"]` unless the caller picks), empty `triageAgent` and
`reportAgent` maps for the connector input mappings, the four triage signals, `aiModel` (the model
the AI Agent tasks call), `slaOverride` and `regulatoryDeadline` (`PT72H` unless overridden).

Those signals are what steer the DMNs, and they come off the request:

| Send | You get |
|---|---|
| nothing | P1, regulatory notification required |
| `attackConfirmed:false` | P4, auto-closes, no human tasks |
| `assetCriticality:"HIGH", dataExposed:false` | P2, regulatory task skipped |
| `assetCriticality:"MEDIUM"` | P3 |

In production the AI triage step would fill these in rather than the caller.

Every worker below is idempotent: it reads `businessKey`, and the framework's `IdempotencyGuard`
drops duplicate deliveries on the floor.

### Steps in execution order

| # | BPMN element | Type | Worker class / decision | What it does | Variables it writes |
|---|---|---|---|---|---|
| 1 | AI Threat Triage (`Task_TriageAI`) | AI Agent connector (OpenAI `gpt-4o-mini`) | - | Prompts the LLM with `title`+`source` for a triage summary. On failure → BPMN error → boundary → step 2 | `triageReport` (LLM text) |
| 2 | Record Triage (`Task_Triage`) | job worker `triage-threat` | `TriageWorker` | Domain: RAISED → TRIAGED. The DMN input signals come from the start variables (request-driven), so this worker only advances domain state | - |
| 3 | Classify Incident (`Task_Classify`) | DMN business rule task | decision `incident-classification` (hit policy FIRST) | Reads `attackConfirmed/assetCriticality/dataExposed` (from the request/start vars) → severity | `severity` (P1/P2/P3/P4) |
| 4 | Record Classification (`Task_RecordClass`) | job worker `record-classification` | `RecordClassificationWorker` | Domain: TRIAGED → CLASSIFIED, saves severity; derives the SLA | `slaDuration` (P1→`PT4H`, P2→`PT8H`, P3→`PT24H`) |
| 5 | P4 / false positive? (`Gateway_P4`) | exclusive gateway | - | If `severity="P4"` → step 5a; else → parallel split (step 6) | - |
| 5a | Auto Close (P4) (`Task_AutoClose`) | job worker `auto-close` | `AutoCloseWorker` | Domain: → AUTO_CLOSED. Ends at "Auto-closed" | - |
| 6 | Response streams (`Gateway_Split`) | parallel gateway | - | Fans out into 4 concurrent branches (A-D) | - |
| A1 | Isolate Systems (`Task_Isolate`, Containment sub-proc) | job worker `isolate-systems` | `IsolationWorker` | Isolates hosts. If `forceIsolationFailure=true` → `businessError("ISOLATION_FAILED")` → error boundary → A2 | `isolated=true` |
| A2 | Handle Isolation Failure (`Task_HandleIsoFail`) | 🧑 human task | group incident-commander, form `handle-isolation-failure-form` | Commander records manual containment (only on isolation failure) | (form fields) |
| A3 | Containment Verification (`Task_ContainVerify`) | 🧑 human task | soc-analyst, `containment-verification-form` | SOC analyst confirms containment | (form fields) |
| B1 | Collect Evidence (`Task_CollectEvidence`, Forensics sub-proc) | job worker `collect-evidence` | `EvidenceCollectionWorker` | Collects forensic evidence | `evidenceCollected=true` |
| B2 | Forensic Analysis (`Task_ForensicAnalysis`) | 🧑 human task | forensics-lead, `forensic-analysis-form` | Forensics lead records root cause / scope | (form fields) |
| C1 | Notify Stakeholders (`Task_Notify`) | job worker `notify-stakeholders` | `NotificationWorker` | Notifies stakeholders | `stakeholdersNotified=true` |
| D1 | Block Malicious IP (`Task_BlockIp`, ad-hoc) | job worker `block-ip` | `BlockIpWorker` | Blocks malicious IPs/domains | `ipBlocked=true` |
| D2 | Revoke Credentials (`Task_RevokeCredentials`, ad-hoc) | job worker `revoke-credentials` | `RevokeCredentialsWorker` | Revokes compromised credentials | `credentialsRevoked=true` |
| D3 | Deploy Patch (`Task_DeployPatch`, ad-hoc) | job worker `deploy-patch` | `DeployPatchWorker` | Deploys an emergency patch (only if the commander activates it) | `patchDeployed=true` |
| 7 | Join (`Gateway_Join`) | parallel join | - | Waits for branches A/B/C/D to complete | - |
| 8 | CISO Review (`Task_CisoReview`) | 🧑 human task | ciso, `ciso-review-form`. Non-interrupting SLA timer (`=slaDuration`) → 8a if overdue | CISO accepts residual risk, authorizes recovery | (form fields) |
| 8a | Escalate SLA Breach (`Task_EscalateSla`) | job worker `escalate` | `EscalationWorker` | Fires in parallel if the SLA is breached | `escalated=true` |
| 9 | Restore Services (`Task_Restore`, Recovery sub-proc) | job worker `restore-services` | `RestoreServicesWorker` | Domain: CLASSIFIED → RECOVERING | `servicesRestored=true` |
| 10 | Integrity Verification (`Task_IntegrityVerify`) | 🧑 human task | soc-analyst, `integrity-verification-form` | Verifies restored systems are clean | (form fields) |
| 11 | Regulatory Notification Required? (`Task_RegDecision`) | DMN business rule task | decision `regulatory-notification` (FIRST) | Reads `dataExposed`+`recordCount` → boolean | `regulatoryRequired` (e.g. true) |
| 12 | Regulatory required? (`Gateway_Reg`) | exclusive gateway | - | `true` → 12a; else skip to step 13 | - |
| 12a | File Regulatory Notification (`Task_FileRegNotification`) | 🧑 human task | legal-compliance, `file-regulatory-notification-form`. Non-interrupting 72h timer → 12b | Legal files notices within the 72-hour deadline | (form fields) |
| 12b | Escalate to CISO (72h) (`Task_EscalateCiso`) | job worker `escalate` | `EscalationWorker` | Fires if the 72h deadline passes | `escalated=true` |
| 13 | AI Post-Incident Report (`Task_ReportAI`) | AI Agent connector (OpenAI) | - | LLM writes the report from `title`+`severity`+`triageReport`. On failure → boundary → 13a | `postIncidentReport` (LLM text) |
| 13a | Generate Report (fallback) (`Task_Report`) | job worker `generate-report` | `PostIncidentReportWorker` | Fallback placeholder report if the AI step fails | `postIncidentReport` (placeholder) |
| 14 | Incident Closure (`Task_Closure`) | 🧑 human task | incident-commander, `incident-closure-form` | Commander reviews the report and closes | (form fields) |
| 15 | Close Incident (`Task_Close`) | job worker `close-incident` | `CloseIncidentWorker` | Domain: RECOVERING → CLOSED. Ends at "Closed" | - |

`incidents.status` walks `RAISED → TRIAGED → CLASSIFIED → RECOVERING → CLOSED`, or jumps straight to
`AUTO_CLOSED` on the P4 branch.

---

## C. Completing the human tasks

The automated steps look after themselves; the instance parks at each human task. You can clear
them in Tasklist, or through the API, which has the side benefit of recording what was submitted
to `incident_task_outcomes`:

```powershell
# 1. list active tasks for the incident (key + name)
curl http://localhost:8080/incidents/<id>/tasks

# 2. complete one by its key (form data goes into the process AND is recorded)
curl -X POST http://localhost:8080/incidents/<id>/tasks/<userTaskKey>/complete -H "Content-Type: application/json" -d "{\"completedBy\":\"soc.analyst\",\"variables\":{\"containmentVerified\":true}}"

# 3. see recorded outcomes / the incident state
curl http://localhost:8080/incidents/<id>/tasks/outcomes
curl http://localhost:8080/incidents/<id>
```

For a P1 the order is: Containment Verification and Forensic Analysis (both open at once), then
CISO Review, Integrity Verification, File Regulatory Notification, Incident Closure. Closing the
last one lets `close-incident` fire and the status goes to CLOSED.

---

## Notes

The AI tasks only produce real text if `OPENAI_API_TOKEN` is set as a cluster secret in Console.
Without it they error, the boundary catches it, and the fallback worker runs instead - so the flow
completes either way.

The workers are stubs. They return the variables listed above rather than calling a real SIEM,
firewall, IAM or mail system; the orchestration is the point here.

## Forcing the exception paths

The five boundary events need an outage, a failed isolation and a 72-hour wait to happen naturally,
so the raise body takes overrides:

| Field | Trips |
|---|---|
| `forceIsolationFailure:true` | error boundary on Isolate Systems → Handle Isolation Failure |
| `forceAiFailure:true` | error boundaries on both AI Agent tasks → fallback workers |
| `slaDuration:"PT20S"` | SLA timer on CISO Review |
| `regulatoryDeadline:"PT20S"` | 72h timer on File Regulatory Notification |
| `responseActions:[...]` | which ad-hoc actions run (`Task_BlockIp`, `Task_RevokeCredentials`, `Task_DeployPatch`) |

`forceAiFailure` sets the `aiModel` variable to a model id OpenAI rejects, so the connector really
gets a 4xx and `errorExpression` really raises `AI_STEP_FAILED` - nothing is stubbed out.

Each automated one writes a row to `GET /incidents/{id}/tasks/outcomes` with `completedBy` set to
`system:process`, which is how you prove it fired without reading the logs. Sending all five at
once on a P1 hits every boundary event and still ends at CLOSED.
