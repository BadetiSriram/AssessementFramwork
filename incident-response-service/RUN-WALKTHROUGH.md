# incident-response-service — Run & Process Walkthrough (UC4)

How to run the backend, and every step of the `incident-response` process from SIEM alert to
CLOSED — each worker, what it does, and the variables it produces.

---

## A. How to run the backend

**Prerequisites**
1. **PostgreSQL 17 running**, with the database: `CREATE DATABASE incident_response OWNER postgres;`
   (login `postgres` / `postgres`).
2. **Framework in `~/.m2`** — from the framework repo run `mvn install -DskipTests` (once, or after
   framework changes).
3. **`application-local.yml`** present with your Camunda 8.9 SaaS credentials (cluster-id, region,
   client-id, client-secret). It is git-ignored — copy from `application-local.yml.example`.

**Start it**
```powershell
cd C:\Users\Sriram.Badeti\Desktop\Assessment\incident-response-service
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

**What happens at startup (in order)**
1. Hikari connects to Postgres.
2. **Flyway** runs migrations → `Successfully applied 3 migrations` (V1 framework tables,
   V2 `incidents`, V3 `incident_task_outcomes`).
3. Hibernate **validates** entity mappings against the tables (`ddl-auto=validate`).
4. **CamundaDeploymentConfig** deploys to the cluster:
   `Deployed Processes: <incident-response:N>`,
   `Deployed Decisions: <incident-classification:N>,<regulatory-notification:N>`,
   `Deployed Forms: <7 forms>`.
5. **13 job workers register** (one log line each): `triage-threat, record-classification,
   auto-close, isolate-systems, collect-evidence, notify-stakeholders, restore-services,
   close-incident, generate-report, block-ip, revoke-credentials, deploy-patch, escalate`.
6. `Started IncidentResponseApplication` — listening on **8080** (Swagger at
   `/swagger-ui/index.html`).

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

## B. The process, step by step (start → end)

### Start — `POST /incidents`
`IncidentController → IncidentService.raiseIncident()`:
- Creates the `Incident` aggregate → status **RAISED**, saves it to Postgres (`incidents` table).
- Calls `ProcessService.start("incident-response", …)` → **Camunda creates the process instance**;
  the returned **process instance key** is stored on the incident (used later to find/complete its
  user tasks).
- **Start variables handed to Camunda:** `businessKey` (= incidentId), `incidentId`, `title`,
  `source`, `forceIsolationFailure` (default false), `triageReport` (""), `responseActions`
  (`["Task_BlockIp","Task_RevokeCredentials"]`), `triageAgent` ({}), `reportAgent` ({}), and the
  **triage signals that drive the DMNs** — `attackConfirmed` (default `true`), `assetCriticality`
  (default `"HIGH"`), `dataExposed` (default `true`), `recordCount` (default `25000`). These are
  **request-driven**: supply any of them on `POST /incidents` to steer the classification
  (e.g. `attackConfirmed:false` → P4 auto-close; `assetCriticality:"HIGH", dataExposed:false` → P2;
  `assetCriticality:"MEDIUM"` → P3). Omitted → the P1 defaults above. In production these would come
  from the AI triage / enrichment step.

Every worker below is **idempotent** — it reads `businessKey` and the framework's `IdempotencyGuard`
silently skips duplicate re-deliveries.

### Steps in execution order

| # | BPMN element | Type | Worker class / decision | What it does | Variables it writes |
|---|---|---|---|---|---|
| 1 | **AI Threat Triage** (`Task_TriageAI`) | AI Agent connector (OpenAI `gpt-4o-mini`) | — | Prompts the LLM with `title`+`source` for a triage summary. On failure → BPMN error → boundary → step 2 | `triageReport` (LLM text) |
| 2 | **Record Triage** (`Task_Triage`) | job worker `triage-threat` | `TriageWorker` | Domain: **RAISED → TRIAGED**. The DMN input signals come from the start variables (request-driven), so this worker only advances domain state | — |
| 3 | **Classify Incident** (`Task_Classify`) | DMN business rule task | decision `incident-classification` (hit policy FIRST) | Reads `attackConfirmed/assetCriticality/dataExposed` (from the request/start vars) → severity | `severity` (**P1**/P2/P3/P4) |
| 4 | **Record Classification** (`Task_RecordClass`) | job worker `record-classification` | `RecordClassificationWorker` | Domain: **TRIAGED → CLASSIFIED**, saves severity; derives the SLA | `slaDuration` (P1→`PT4H`, P2→`PT8H`, P3→`PT24H`) |
| 5 | **P4 / false positive?** (`Gateway_P4`) | exclusive gateway | — | If `severity="P4"` → step 5a; else → parallel split (step 6) | — |
| 5a | **Auto Close (P4)** (`Task_AutoClose`) | job worker `auto-close` | `AutoCloseWorker` | Domain: **→ AUTO_CLOSED**. Ends at "Auto-closed" | — |
| 6 | **Response streams** (`Gateway_Split`) | parallel gateway | — | Fans out into 4 concurrent branches (A–D) | — |
| A1 | **Isolate Systems** (`Task_Isolate`, Containment sub-proc) | job worker `isolate-systems` | `IsolationWorker` | Isolates hosts. If `forceIsolationFailure=true` → `businessError("ISOLATION_FAILED")` → **error boundary** → A2 | `isolated=true` |
| A2 | **Handle Isolation Failure** (`Task_HandleIsoFail`) | 🧑 human task | group **incident-commander**, form `handle-isolation-failure-form` | Commander records manual containment (only on isolation failure) | (form fields) |
| A3 | **Containment Verification** (`Task_ContainVerify`) | 🧑 human task | **soc-analyst**, `containment-verification-form` | SOC analyst confirms containment | (form fields) |
| B1 | **Collect Evidence** (`Task_CollectEvidence`, Forensics sub-proc) | job worker `collect-evidence` | `EvidenceCollectionWorker` | Collects forensic evidence | `evidenceCollected=true` |
| B2 | **Forensic Analysis** (`Task_ForensicAnalysis`) | 🧑 human task | **forensics-lead**, `forensic-analysis-form` | Forensics lead records root cause / scope | (form fields) |
| C1 | **Notify Stakeholders** (`Task_Notify`) | job worker `notify-stakeholders` | `NotificationWorker` | Notifies stakeholders | `stakeholdersNotified=true` |
| D1 | **Block Malicious IP** (`Task_BlockIp`, ad-hoc) | job worker `block-ip` | `BlockIpWorker` | Blocks malicious IPs/domains | `ipBlocked=true` |
| D2 | **Revoke Credentials** (`Task_RevokeCredentials`, ad-hoc) | job worker `revoke-credentials` | `RevokeCredentialsWorker` | Revokes compromised credentials | `credentialsRevoked=true` |
| D3 | **Deploy Patch** (`Task_DeployPatch`, ad-hoc) | job worker `deploy-patch` | `DeployPatchWorker` | Deploys an emergency patch (only if the commander activates it) | `patchDeployed=true` |
| 7 | **Join** (`Gateway_Join`) | parallel join | — | Waits for branches A/B/C/D to complete | — |
| 8 | **CISO Review** (`Task_CisoReview`) | 🧑 human task | **ciso**, `ciso-review-form`. **Non-interrupting SLA timer** (`=slaDuration`) → 8a if overdue | CISO accepts residual risk, authorizes recovery | (form fields) |
| 8a | **Escalate SLA Breach** (`Task_EscalateSla`) | job worker `escalate` | `EscalationWorker` | Fires in parallel if the SLA is breached | `escalated=true` |
| 9 | **Restore Services** (`Task_Restore`, Recovery sub-proc) | job worker `restore-services` | `RestoreServicesWorker` | Domain: **CLASSIFIED → RECOVERING** | `servicesRestored=true` |
| 10 | **Integrity Verification** (`Task_IntegrityVerify`) | 🧑 human task | **soc-analyst**, `integrity-verification-form` | Verifies restored systems are clean | (form fields) |
| 11 | **Regulatory Notification Required?** (`Task_RegDecision`) | DMN business rule task | decision `regulatory-notification` (FIRST) | Reads `dataExposed`+`recordCount` → boolean | `regulatoryRequired` (e.g. **true**) |
| 12 | **Regulatory required?** (`Gateway_Reg`) | exclusive gateway | — | `true` → 12a; else skip to step 13 | — |
| 12a | **File Regulatory Notification** (`Task_FileRegNotification`) | 🧑 human task | **legal-compliance**, `file-regulatory-notification-form`. **Non-interrupting 72h timer** → 12b | Legal files notices within the 72-hour deadline | (form fields) |
| 12b | **Escalate to CISO (72h)** (`Task_EscalateCiso`) | job worker `escalate` | `EscalationWorker` | Fires if the 72h deadline passes | `escalated=true` |
| 13 | **AI Post-Incident Report** (`Task_ReportAI`) | AI Agent connector (OpenAI) | — | LLM writes the report from `title`+`severity`+`triageReport`. On failure → boundary → 13a | `postIncidentReport` (LLM text) |
| 13a | **Generate Report (fallback)** (`Task_Report`) | job worker `generate-report` | `PostIncidentReportWorker` | Fallback placeholder report if the AI step fails | `postIncidentReport` (placeholder) |
| 14 | **Incident Closure** (`Task_Closure`) | 🧑 human task | **incident-commander**, `incident-closure-form` | Commander reviews the report and closes | (form fields) |
| 15 | **Close Incident** (`Task_Close`) | job worker `close-incident` | `CloseIncidentWorker` | Domain: **RECOVERING → CLOSED**. Ends at "Closed" | — |

**Domain lifecycle** (persisted in `incidents.status`):
`RAISED → TRIAGED → CLASSIFIED → RECOVERING → CLOSED` (or `→ AUTO_CLOSED` on the P4 branch).

---

## C. Completing the human tasks (from the API)

The automated steps run on their own; the instance **waits** at the 🧑 human tasks. Complete them
from the API (or in Tasklist), which also **persists each outcome to `incident_task_outcomes`**:

```powershell
# 1. list active tasks for the incident (key + name)
curl http://localhost:8080/incidents/<id>/tasks

# 2. complete one by its key (form data goes into the process AND is recorded)
curl -X POST http://localhost:8080/incidents/<id>/tasks/<userTaskKey>/complete -H "Content-Type: application/json" -d "{\"completedBy\":\"soc.analyst\",\"variables\":{\"containmentVerified\":true}}"

# 3. see recorded outcomes / the incident state
curl http://localhost:8080/incidents/<id>/tasks/outcomes
curl http://localhost:8080/incidents/<id>
```

Order for a P1 incident: **Containment Verification + Forensic Analysis** (parallel) → **CISO
Review** → **Integrity Verification** → **File Regulatory Notification** → **Incident Closure** →
the `close-incident` worker fires → **status CLOSED**.

---

## Notes
- The **AI Agent tasks (steps 1 and 13)** produce real LLM text only if `OPENAI_API_TOKEN` is set as
  a cluster secret in Camunda Console; otherwise they error → the **error boundary** routes to the
  job worker (step 2 / 13a) and the flow continues.
- The **job workers are runnable placeholders** — they log/return the variables above rather than
  calling real SIEM / firewall / IAM / email systems (expected for the assessment, which grades
  orchestration).
- To force the exception path (failed isolation → escalation to the incident commander), start with
  `{"title":"…","source":"SIEM","forceIsolationFailure":true}`.
