# Understanding the Project — Why We Built It This Way (UC4)

**Read this to be able to explain the whole project to your lead.** For every BPMN element and
every piece of Java code it answers three questions: **What is it? Why did we use it here? How does
it work?** It ends with a full end-to-end **scenario** you can narrate, and a **Q&A prep** section.

- Project: `incident-response-service` — **Use Case 4, Cyber Security Incident Response** on Camunda 8.9 SaaS.
- Built on the base **`camunda-process-framework`** (same as `order-service-sample`).
- Process id: `incident-response`. Base package: `com.aaseya.incident`. Database: PostgreSQL 17 (`incident_response`).

---

## Section 1 — The business problem (why this project exists)

An enterprise Security Operations Center (SOC) receives **thousands of alerts a day**. Today:
runbooks live in wikis, response actions are done by hand, and the **72-hour regulatory notification
deadline** (GDPR and similar) is tracked in email. That is slow, inconsistent, and risky.

**Goal:** one orchestrated, auditable journey that:
1. **Triages** each alert with AI,
2. **Classifies** severity (P1–P4) with business rules,
3. Runs **containment and forensics in parallel**,
4. Lets the **Incident Commander pick response actions dynamically**,
5. **Enforces the regulatory decision** (with a hard 72h timer) before closure,
6. Produces a **post-incident report** and closes the incident.

The distinguishing capability graded in UC4 is **timers & escalation** (time-bound response), so
that is modelled deliberately, not as an afterthought.

---

## Section 2 — Solution at a glance (the shape)

Two cooperating halves:

- **The process model (Camunda 8.9):** BPMN orchestrates the *flow*; DMN makes the *decisions*; AI
  connectors do the *reasoning*; Tasklist forms handle the *human steps*; timers enforce the *deadlines*.
- **The backend service (Spring Boot 4 on the framework):** Spring Boot **job workers** do the
  automated work; a **domain aggregate** (`Incident`) holds state in PostgreSQL; REST endpoints
  start incidents and complete tasks.

```
SIEM alert ──▶ [Spring Boot service: POST /incidents] ──▶ starts the process in Camunda
                                                             │
Camunda (Zeebe) drives the flow ──▶ calls back into ◀───────┘
   job workers (automated steps), DMN (decisions),
   AI connectors (triage/report), Tasklist (human tasks),
   timers (SLA + 72h)
```

**Why split it this way?** The engine owns *coordination* (who runs when, waiting, timers, retries,
parallelism) so our Java code never has to. Our Java code owns *business behaviour* (state changes,
persistence, real integrations). This is the core value of a process orchestrator.

---

## Section 3 — Why this technology stack (each choice justified)

| Choice | Why we used it |
|---|---|
| **Camunda 8.9 SaaS** | The assessment platform; gives BPMN/DMN engine (Zeebe), Operate (monitoring), Tasklist (human tasks), Web Modeler, and connectors — all managed. |
| **BPMN** for the flow | Standard, visual, executable. The same diagram is the documentation *and* the running process — business and engineers read one artifact. |
| **DMN** for decisions | Rules (severity, regulatory) change often and must be readable/auditable by non-developers. A decision table is versionable and unit-testable; hard-coded `if/else` in Java is not (and the rubric marks it down). |
| **AI Agent connector** for triage/report | Natural-language reasoning (enrichment, summarising) is exactly what an LLM is good at and what rules/code are bad at. A connector keeps the credential and call out of our code. |
| **Spring Boot 4 job workers** | The rule: every automated service task is a Spring Boot worker. Keeps business logic in our tested, version-controlled service, not in the model. |
| **Base framework** (`camunda-process-framework`) | Gives idempotency, error classification, metrics, layering, and a state machine for free — we only write `doWork()`. (Section 5.) |
| **PostgreSQL 17** | Durable store for the incident aggregate and the human-task outcomes; production-grade (we migrated off H2). |
| **Flyway** | Versioned, repeatable schema migrations (V1 framework tables, V2 incidents, V3 outcomes). |

---

## Section 4 — Why build on the base framework (what it gives us)

We did **not** scaffold a new project — ground rule #1 requires the base framework, and it does real
work for us. What it provides and why it matters here:

| Framework building block | What it gives | Why UC4 needs it |
|---|---|---|
| **`BaseWorker<V>`** | A worker template — you implement `varsType()` + `doWork()`; it handles activation, variable mapping, metrics, MDC logging, error translation. | We wrote **13 workers** with almost no boilerplate; all behave consistently. |
| **`IdempotencyGuard`** | Skips a job if the same `businessKey` was already processed (via a `worker_execution` table). | UC4 demands **re-delivery-safe** workers; a SIEM can fire the same alert twice, and Zeebe re-delivers jobs on retry. |
| **`WorkResult`** | `completed(vars)` / `businessError(code,msg)` / `compensated()` — the worker's verdict. | Lets a worker cleanly signal a **business failure** (failed isolation) vs a technical one. |
| **`AuditableEntity<S>`** | A guarded **state machine** base class + audit columns (created/updated by/at). | The `Incident` status can only move through legal transitions; illegal moves throw. Free audit trail. |
| **`ProcessService`** | A thin port to start processes / complete user tasks without importing the Camunda SDK in business code. | Keeps our application layer clean (see layering below). |
| **6 ArchUnit rules** | Compile-time-ish tests that enforce hexagonal layering. | Guarantees workers live in `infrastructure.camunda`, only that package touches `io.camunda`, domain stays pure — the rubric grades "logic in the right place". |

**Business-vs-technical error rule (important, repeated below):**
- `WorkResult.businessError(code,…)` → framework issues a **BPMN error** → caught by a boundary event
  → handled in the flow (e.g., escalate to a human). *Expected* deviation.
- Throwing a `RetryableException` → Zeebe **retries**, and on exhaustion raises an **incident in
  Operate** for an operator. *Unexpected* technical fault.

---

## Section 5 — The BPMN model, element by element (the big one)

The process id is `incident-response`. Below, every element grouped by phase, with **why it is
there** and **how it works**. (File: `src/main/resources/processes/incident-response.bpmn`.)

### 5.1 Intake & classification (the "decide what this is" phase)

| Element | BPMN type | Why here / purpose | How it works | Key variables |
|---|---|---|---|---|
| **SIEM alert** (`StartEvent_1`) | Start event | The incident begins when an alert arrives. | Started by our service (`POST /incidents`) via `ProcessService.start()`. | `businessKey`, `incidentId`, `title`, `source`, triage signals |
| **AI Threat Triage** (`Task_TriageAI`) | AI Agent connector task | UC4 requires an **AI triage** step; summarising/enriching an alert is an LLM strength. | Calls OpenAI `gpt-4o-mini`; writes a triage summary. On error → BPMN error `AI_STEP_FAILED`. | writes `triageReport` |
| **AI failed** (`Boundary_TriageAiFailed`) | Error boundary event | **Resilience** — an AI outage or missing key must never stall an incident. | Catches `AI_STEP_FAILED`, routes to the worker fallback. | — |
| **Record Triage** (`Task_Triage`) | Job worker `triage-threat` | Advance the domain state to TRIAGED; also the **fallback** target if the AI step failed. | `TriageWorker` → `incidentService.markTriaged()`. | — (signals come from start) |
| **Classify Incident** (`Task_Classify`) | DMN business rule task | UC4 requires severity classification via **rules, not code**. | Calls DMN `incident-classification`; result → `severity`. | reads `attackConfirmed/assetCriticality/dataExposed`, writes `severity` |
| **Record Classification** (`Task_RecordClass`) | Job worker `record-classification` | Persist severity and **derive the SLA** the timers will use. | `RecordClassificationWorker` → status CLASSIFIED; maps P1→PT4H, P2→PT8H, P3→PT24H. | writes `slaDuration` |
| **P4 / false positive?** (`Gateway_P4`) | Exclusive gateway | Not every alert is real — **false positives must auto-close**, not consume the full response. | If `severity="P4"` → auto-close branch; else → parallel response. | reads `severity` |
| **Auto Close (P4)** (`Task_AutoClose`) | Job worker `auto-close` | Cleanly close a false positive with an audit note. | `AutoCloseWorker` → status AUTO_CLOSED → end. | — |

**Why AI *then* a worker *then* DMN?** The AI produces a human-readable summary; the DMN needs
*structured* facts. We keep them separate: the LLM writes `triageReport` (prose), while the
structured signals that drive the DMN (`attackConfirmed`, `assetCriticality`, `dataExposed`,
`recordCount`) are **request-driven process variables** (supplied on `POST /incidents`, defaulting
to a high-severity P1). In production those signals would come from AI/enrichment; separating them
keeps the DMN deterministic and demoable.

### 5.2 Parallel response (the "act on P1–P3" phase)

| Element | BPMN type | Why here / purpose | How it works |
|---|---|---|---|
| **Response streams** (`Gateway_Split`) | Parallel gateway (split) | Containment, forensics, notification and dynamic actions are **independent and time-critical** — running them in sequence would waste precious minutes. | Forks **4 concurrent branches**. |
| **Containment** (`SubProcess_Containment`) | Embedded sub-process | Groups the containment steps into one logical, collapsible unit that shares the incident's variables. | Isolate → (on failure, escalate) → SOC verification. |
| **Forensics** (`SubProcess_Forensics`) | Embedded sub-process | Groups evidence collection + analysis. | Collect evidence → forensic analysis (human). |
| **Notify Stakeholders** (`Task_Notify`) | Job worker `notify-stakeholders` | Automated stakeholder comms — a single automated step, so a plain worker. | Sends notifications. |
| **Response Actions (ad-hoc)** (`AdHoc_ResponseActions`) | **Ad-hoc sub-process** | UC4 rule #6: the commander **selects only the actions needed, at runtime** — that is exactly an ad-hoc sub-process, not a gateway chain. | Activates the inner tasks named in `responseActions`; more can be activated (and repeated) at runtime. |
| **Join** (`Gateway_Join`) | Parallel gateway (join) | Recovery must not start until **all four** streams finish. | Waits for all 4 branches. |

**Inside Containment — the error-handling showcase:**

| Element | Type | Why here | How it works |
|---|---|---|---|
| **Isolate Systems** (`Task_Isolate`) | Job worker `isolate-systems` | Automated network/host isolation. | `IsolationWorker`; if it fails → `businessError("ISOLATION_FAILED")`. |
| **Isolation failed** (`Boundary_IsolationFailed`) | Error boundary event | UC4: *"failed isolation raises a BPMN error that escalates to the incident commander rather than silently retrying forever."* | Catches `ISOLATION_FAILED`, routes to a human task. |
| **Handle Isolation Failure** (`Task_HandleIsoFail`) | User task (**incident-commander**) | A human must contain manually when automation can't. | Tasklist form `handle-isolation-failure-form`; then flows to verification. |
| **Containment Verification** (`Task_ContainVerify`) | User task (**soc-analyst**) | A person confirms containment actually worked. | Tasklist form; ends the sub-process. |

**Why embedded sub-processes and not call activities?** Containment/Forensics/Recovery are **not
reused** by other processes, they **share the incident's variables**, and embedding keeps everything
in **one deployable, versioned unit**. A call activity would add deployment, versioning, and
variable-mapping overhead for no reuse benefit.

**Why the ad-hoc sub-process (in one line):** so the Incident Commander can **compose and repeat**
only the counter-measures a given incident needs (block IP / revoke credentials / deploy patch),
instead of forcing every incident down a fixed pre-drawn path. Its inner tasks are intentionally
**independent (no inner gateways)** — a known Camunda 8.9 limitation is that a parallel join *inside*
an ad-hoc sub-process is broken.

### 5.3 Review, recovery & the regulatory deadline (the "close it safely" phase)

| Element | BPMN type | Why here / purpose | How it works | Variables |
|---|---|---|---|---|
| **CISO Review** (`Task_CisoReview`) | User task (**ciso**) | A manager must accept residual risk and authorize recovery. | Tasklist form `ciso-review-form`. | — |
| **SLA breach** (`Boundary_CisoSla`) | **Non-interrupting timer** boundary | Severity-based SLA on a key human task (UC4 CORE). | `timeDuration = =slaDuration`; **doesn't cancel** the task, escalates in parallel. | reads `slaDuration` |
| **Escalate SLA Breach** (`Task_EscalateSla`) | Job worker `escalate` | Someone gets paged if the CISO is slow — the task itself continues. | `EscalationWorker` → separate end event. | — |
| **Recovery** (`SubProcess_Recovery`) | Embedded sub-process | Group restore + integrity check. | Restore services → integrity verification (human). | status → RECOVERING |
| **Restore Services** (`Task_Restore`) | Job worker `restore-services` | Bring systems back from a clean state. | `RestoreServicesWorker` → status RECOVERING. | — |
| **Integrity Verification** (`Task_IntegrityVerify`) | User task (**soc-analyst**) | A person verifies restored systems are actually clean. | Tasklist form `integrity-verification-form`. | — |
| **Regulatory Notification Required?** (`Task_RegDecision`) | DMN business rule task | Whether we must notify regulators is a **rule** (data categories, record counts), not code. | Calls DMN `regulatory-notification`. | writes `regulatoryRequired` |
| **Regulatory required?** (`Gateway_Reg`) | Exclusive gateway | Only file notices when the rule says so. | `true` → file notification; else skip. | reads `regulatoryRequired` |
| **File Regulatory Notification** (`Task_FileRegNotification`) | User task (**legal-compliance**) | Legal files the notices. | Tasklist form `file-regulatory-notification-form`. | — |
| **72h deadline** (`Boundary_Reg72h`) | **Non-interrupting timer** boundary | The headline UC4 requirement — the **72-hour regulatory deadline with escalation**. | Fixed `PT72H`; escalates to the CISO without cancelling the filing task. | — |
| **Escalate to CISO (72h)** (`Task_EscalateCiso`) | Job worker `escalate` | If the deadline is at risk, the CISO is alerted. | `EscalationWorker` → separate end event. | — |
| **Regulatory merge** (`Gateway_RegMerge`) | Exclusive gateway | Rejoin the "required" and "not required" paths. | Both paths continue to the report. | — |
| **AI Post-Incident Report** (`Task_ReportAI`) | AI Agent connector task | UC4 requires an **AI report** (timeline, root cause, impact, lessons). | OpenAI; on error → `AI_STEP_FAILED`. | writes `postIncidentReport` |
| **AI failed** (`Boundary_ReportAiFailed`) | Error boundary event | Same resilience pattern as triage. | Routes to the worker fallback. | — |
| **Generate Report (fallback)** (`Task_Report`) | Job worker `generate-report` | Guarantees a report exists even if the AI is down. | `PostIncidentReportWorker` writes a placeholder. | writes `postIncidentReport` |
| **Incident Closure** (`Task_Closure`) | User task (**incident-commander**) | The commander reviews the report and makes the final call. | Tasklist form `incident-closure-form`. | — |
| **Close Incident** (`Task_Close`) | Job worker `close-incident` | Persist the terminal state. | `CloseIncidentWorker` → status CLOSED → end. | — |

**Why non-interrupting timers (`cancelActivity="false"`)?** An SLA breach or a 72h warning should
**alert people in parallel** — it must not **cancel** the human task that is still legitimately in
progress. Interrupting timers would throw away work; non-interrupting timers escalate while the task
continues. That is the correct semantics for "deadline reminder / escalation."

**Why two BPMN error types are defined** (`ISOLATION_FAILED`, `AI_STEP_FAILED`): both are *expected*
business deviations with a defined recovery in the flow (escalate to a human / fall back to a
worker), so they are modelled as **BPMN errors caught by boundary events**, not left to become
technical incidents.

---

## Section 6 — The DMN decisions (why rules, not code)

Two tables, both **hit policy FIRST**, invoked from business rule tasks (`zeebe:calledDecision`).

**1) Incident Classification** — inputs `attackConfirmed`, `assetCriticality`, `dataExposed`; output
`severity` (P1–P4). Rules are ordered by precedence: false-positive first (→ P4), then most-severe
downward. `severity` then drives the SLA timer.

**2) Regulatory Notification Required** — inputs `dataExposed`, `recordCount`; output
`regulatoryRequired` (boolean).

**Why hit policy FIRST (be ready for this question):** each decision needs a **single deterministic
output** and the rules are an **ordered precedence list**. FIRST encodes that intent directly and
**tolerates overlapping conditions**, unlike UNIQUE (which forbids overlap and is brittle as rules
grow). COLLECT/RULE-ORDER don't apply (no aggregation). Inputs come from process variables, so the
tables stay **versionable and unit-testable** in isolation.

---

## Section 7 — The AI Agent connector steps (why AI, and how it's safe)

Both AI steps use the **AI Agent Task** element template (`io.camunda.agenticai:aiagent:1`,
template `io.camunda.connectors.agenticai.aiagent.v1`), provider **OpenAI `gpt-4o-mini`**, auth via
the cluster secret **`{{secrets.OPENAI_API_TOKEN}}`**.

- **Prompt design** — a **system prompt** sets the role ("SOC threat-triage assistant" /
  "incident-response analyst") and required output shape; a **user prompt** injects process variables
  (`title`, `source` for triage; `title`, `severity`, `triageReport` for the report).
- **Output mapping** — `resultExpression = {triageReport: response.responseText}` (and
  `{postIncidentReport: …}`), so the model's text lands in a clean process variable.
- **Resilience (the key design point)** — `errorExpression` converts any connector failure into a
  BPMN error `AI_STEP_FAILED`; an **error boundary event routes to a job-worker fallback**. So a
  missing key, timeout, or outage **never stalls an incident** — the flow always completes.

**Why a connector rather than a worker for AI?** Ground rule #3 allows connectors specifically for AI
steps; it keeps the API credential and the HTTP/LLM plumbing **out of our codebase** and configurable
in the model.

---

## Section 8 — The Java code, layer by layer (why each layer exists)

Hexagonal layering, enforced by **6 ArchUnit rules**. Direction: **web → application →
infrastructure**; the **domain** is pure.

### 8.1 Domain (`com.aaseya.incident.domain`)
- **`Incident`** — the aggregate. Extends `AuditableEntity<IncidentStatus>` so status changes go
  through a **guarded state machine**: `allowedTransitions()` defines the legal graph
  (RAISED→TRIAGED→CLASSIFIED→RECOVERING→CLOSED, plus →AUTO_CLOSED early). An illegal move throws —
  **the domain protects its own invariants**, no service can corrupt state. Holds `severity`,
  `businessKey`, and `processInstanceKey`.
- **`IncidentStatus`, `IncidentSeverity`** — enums (persisted as strings) = the vocabulary.
- **`IncidentTaskOutcome`** — records each completed human task (who, which element, submitted form
  JSON) for the audit trail.
- **Why the domain is framework-pure:** ArchUnit rule 3 forbids Spring-web / `io.camunda` imports
  here, so the business rules stay testable and independent of the engine.

### 8.2 Application (`…application`)
- **`IncidentService`** — the use-case layer. `raiseIncident()` creates the aggregate, saves it, and
  **starts the process** (via `ProcessService`), storing the returned **process instance key**. It
  builds the **start variables** (including the request-driven triage signals with defaults). The
  worker-callbacks (`markTriaged`, `recordClassification`, `markRecovering`, `close`, `autoClose`)
  each load the aggregate and call a domain method. **Transactions live here** (ArchUnit rule 4 —
  never on the controller).
- **`IncidentTaskService`** — lists an incident's active Tasklist tasks, **completes** them
  (submitting form variables to Camunda), and **persists each outcome**. Enforces "one active task"
  vs "complete by key".
- **Why an application layer at all?** It coordinates domain + persistence + engine without leaking
  web or Camunda types into the domain, and it is where the transaction boundary belongs.

### 8.3 Infrastructure / Camunda (`…infrastructure.camunda`)
- **13 job workers** (all `BaseWorker` subclasses, all here per ArchUnit rule 2). Each declares a
  `@JobWorker(type="…")` whose **type must exactly match** the BPMN `zeebe:taskDefinition type`, a
  typed `varsType()` (a record of only the variables it needs), and a `doWork()` returning a
  `WorkResult`. Representative examples:
  - **`IsolationWorker`** — returns `businessError("ISOLATION_FAILED")` on failure (drives the error
    boundary); otherwise idempotent success.
  - **`RecordClassificationWorker`** — persists severity and **derives `slaDuration`** for the timers.
  - **`TriageWorker`** — advances domain state only (signals now come from the request).
- **`CamundaTaskAdapter`** — the **only** place that talks to the Camunda client for user-task
  search/complete (Orchestration Cluster REST v2). Keeps the SDK out of the application layer.
- **`CamundaDeploymentConfig`** — `@Deployment(resources = {processes/*.bpmn, dmn/*.dmn,
  forms/*.form})` auto-deploys the model on startup (the framework has no auto-deploy; we wired it).
- **Why workers are idempotent:** each passes `businessKey = incidentId`; the framework's
  `IdempotencyGuard` short-circuits duplicate re-deliveries, and the actions are written to be
  naturally repeatable.

### 8.4 Web (`…web`)
- **`IncidentController`** — `POST /incidents` (raise, returns 201 + the incident) and `GET
  /incidents/{id}`. DTO-only, non-transactional (ArchUnit rules 4 & 5).
- **`IncidentTaskController`** — `GET …/tasks`, `POST …/tasks/{key}/complete`, `POST …/tasks/complete`,
  `GET …/tasks/outcomes`. **These APIs are for testing/automation; the graded demo completes human
  tasks in Tasklist UI.**
- **DTOs** (`RaiseIncidentRequest`, `IncidentDto`, `UserTaskView`, `CompleteTaskRequest`,
  `TaskOutcomeView`) — keep the domain out of the HTTP boundary.

### 8.5 Config & persistence
- **`application-local.yml`** — Camunda SaaS credentials + Postgres. **Git-ignored** (secrets); only
  the `.example` with placeholders is committed.
- **Flyway migrations** — `V1` (framework tables incl. `worker_execution` for idempotency), `V2`
  (`incidents`), `V3` (`process_instance_key` + `incident_task_outcomes`). `ddl-auto=validate` — the
  schema is owned by migrations, Hibernate only validates.

### 8.6 Why the 6 ArchUnit rules (the "logic in the right place" guarantee)
Workers only under `infrastructure.camunda`; only that package imports `io.camunda.client`; domain
stays pure; transactions only in the application layer; controllers stay thin; constructor injection.
Result: the layering the rubric grades is **enforced automatically**, not just intended.

---

## Section 9 — Data model (what we persist and why)

| Table | Why it exists | Key columns |
|---|---|---|
| **`incidents`** | The incident aggregate — the business record and its lifecycle. | `id`, `business_key`, `title`, `source`, `severity`, `status`, `process_instance_key`, audit columns |
| **`incident_task_outcomes`** | Proof of *who did what* — every human-task completion (persona action + submitted form data). | `incident_id`, `user_task_key`, `element_id`, `task_name`, `completed_by`, `outcome` (JSON), `created_at` |
| **`worker_execution`** (framework) | Backs the idempotency guard (dedupes job re-delivery). | `business_key`, worker id, status |

**Why store outcomes separately:** it demonstrates the backend isn't a black box — every human
decision is recorded and queryable, independent of Camunda's own history.

---

## Section 10 — Cross-cutting design decisions & trade-offs (chose X over Y)

| Decision | We chose | Over | Why |
|---|---|---|---|
| Sub-process style | **Embedded** | Call activity | No reuse; shared variables; one versioned deployable. |
| Response actions | **Ad-hoc sub-process** | Gateway chain | Genuine runtime, repeatable selection by the commander. |
| Severity/regulatory rules | **DMN (FIRST)** | Java `if/else` | Auditable, versionable, testable; rubric requires it. |
| AI steps | **Connector + boundary fallback** | Worker calling an API / no fallback | Keeps creds out of code; never stalls on AI failure. |
| Isolation failure | **BPMN error → human** | Infinite retry / incident | Expected deviation with a human recovery, per UC4. |
| Timers | **Non-interrupting** | Interrupting | Escalate in parallel without discarding in-progress work. |
| Idempotency | **`businessKey` + guard + naturally idempotent actions** | Nothing | SIEM re-delivery and job retries must be safe. |
| Triage → DMN inputs | **Request-driven variables (defaults)** | Hardcoded in the worker | Deterministic, lets us demo P1–P4; logic still lives in DMN. |
| Deployment | **`@Deployment` on startup** | Manual upload | One command boots and deploys everything reproducibly. |

---

## Section 11 — End-to-end scenario (narrate this to your lead)

**Setup:** service running (`mvn spring-boot:run "-Dspring-boot.run.profiles=local"`), Postgres up,
Operate + Tasklist open.

### Scenario A — the happy path (a real P1 breach → CLOSED)
1. **Alert arrives.** `POST /incidents {"title":"Ransomware + exfiltration on prod-db","source":"SIEM"}`.
   The service saves the incident (**status RAISED**) and starts the process; the **process instance
   key** is stored on the incident.
2. **AI triage** runs (`Task_TriageAI`) and writes `triageReport`. (If OpenAI is unavailable, the
   **error boundary** falls back to the `triage-threat` worker — the flow continues either way.)
3. **Record Triage** worker → **status TRIAGED**.
4. **Classify Incident** DMN reads the signals (default = confirmed/HIGH/data exposed) → **`severity =
   P1`**. **Record Classification** worker → **status CLASSIFIED**, and derives **`slaDuration = PT4H`**.
5. **P4 gateway:** not a false positive → the **parallel gateway** fans out into four streams:
   - **Containment:** `isolate-systems` worker succeeds → waits at **Containment Verification** (SOC
     Analyst) in Tasklist.
   - **Forensics:** `collect-evidence` worker → waits at **Forensic Analysis** (Forensics Lead).
   - **Notification:** `notify-stakeholders` worker runs.
   - **Ad-hoc actions:** the two defaulted actions (**Block IP**, **Revoke Credentials**) run; the
     commander could activate **Deploy Patch** or repeat an action at runtime.
6. **Human work in Tasklist** (the graded part). Complete, as each persona:
   Containment Verification (SOC) + Forensic Analysis (Forensics Lead) → the **join** releases →
   **CISO Review** (CISO; an **SLA timer** rides on it) → **Integrity Verification** (SOC, inside
   Recovery; **status RECOVERING**).
7. **Regulatory decision** DMN reads `dataExposed=true, recordCount=25000` → **`regulatoryRequired =
   true`** → **File Regulatory Notification** (Legal/Compliance) appears, carrying the **72-hour
   timer**.
8. **AI Post-Incident Report** writes `postIncidentReport` (or the fallback worker does).
9. **Incident Closure** (Incident Commander) → the `close-incident` worker fires → **status CLOSED**,
   the instance completes. `incident_task_outcomes` now has a row per human task.

### Scenario B — exception: automated isolation fails
`POST /incidents {"title":"…","source":"SIEM","forceIsolationFailure":true}`. The `isolate-systems`
worker returns `businessError("ISOLATION_FAILED")` → the **error boundary** catches it → **Handle
Isolation Failure** task appears for the **Incident Commander** (manual containment) → then normal
Containment Verification. **The point:** a failed automated action **escalates to a human**, it does
not silently retry forever.

### Scenario C — exception: false positive (auto-close)
`POST /incidents {"title":"Benign alert","source":"SIEM","attackConfirmed":false}`. The DMN returns
**P4** → the **P4 gateway** routes to **Auto Close** → **status AUTO_CLOSED**, no parallel response,
no human tasks. **The point:** noise is closed cheaply.

### Scenario D — exception: 72-hour regulatory deadline
On the regulatory path, if **File Regulatory Notification** isn't completed within **PT72H**, the
**non-interrupting timer** fires **Escalate to CISO** in parallel — the filing task stays open, but
the deadline risk is surfaced. **The point:** the legal deadline is enforced by the engine, not email.

*(For the demo, timers can be shortened, e.g. PT2M, to show them firing live.)*

---

## Section 12 — How each piece maps to the assessment rubric

| Rubric criterion (weight) | Where we satisfy it |
|---|---|
| Process modeling quality (20%) | Clean BPMN, correct gateway/sub-process choices, meaningful names, full DI. |
| DMN design (15%) | Two tables, justified FIRST hit policy, inputs from process variables, versionable. |
| AI Connector usage (15%) | Two AI Agent steps, prompts from variables, output mapped to variables, **failure handling** via boundary→fallback. |
| Ad-hoc sub-process (10%) | Runtime, repeatable action selection by the commander. |
| Spring Boot workers (20%) | Framework layering, 13 idempotent workers, correct types/retries, logic in the right layer (ArchUnit). |
| Error handling & resilience (10%) | BPMN error vs incident used deliberately; bounded retries; escalation. |
| Tasklist & personas (5%) | 7 forms, candidate groups per persona, scoped task variables. |
| Testing evidence & docs (5%) | Happy path to CLOSED + exception paths; this doc + DESIGN-NOTE + RUN-WALKTHROUGH. |

---

## Section 13 — Anticipated lead questions (be ready)

- **"Why Camunda and not just code?"** — Coordination (waiting, timers, parallelism, retries, human
  tasks, monitoring) is complex and cross-cutting; the engine gives it for free and makes the flow
  visible/auditable. We keep business logic in tested Java workers.
- **"Why embedded sub-processes?"** — No reuse, shared variables, one versioned deployable; call
  activities would add overhead for no benefit.
- **"Why an ad-hoc sub-process?"** — The commander selects only the actions needed, at runtime, and
  can repeat them — a gateway chain would hard-code the choice.
- **"Why DMN FIRST?"** — Single deterministic output over an ordered precedence list; tolerant of
  overlap; maintainable and unit-testable.
- **"What if the AI is down?"** — Error boundary falls back to a worker; the incident still closes.
- **"BPMN error vs incident?"** — Expected deviations (failed isolation) → BPMN error → human;
  technical faults → retry → Operate incident.
- **"Is it idempotent / re-delivery safe?"** — Yes: `businessKey` + `IdempotencyGuard` + naturally
  idempotent actions.
- **"Why non-interrupting timers?"** — Escalate in parallel without cancelling legitimate in-progress
  work.
- **"Where are the human tasks done?"** — Tasklist UI in the demo (candidate groups per persona); the
  REST task API exists only for testing/automation.
- **"Why hardcoded-looking triage values?"** — They are **input data**, request-driven with defaults,
  not decision logic; the classification logic lives in the DMN (rule #4 respected).

---

## Section 14 — Quick reference

- **Personas → candidate groups:** SOC Analyst `soc-analyst`, Incident Commander `incident-commander`,
  Forensics Lead `forensics-lead`, CISO `ciso`, Legal/Compliance `legal-compliance`.
- **Status machine:** `RAISED → TRIAGED → CLASSIFIED → RECOVERING → CLOSED`; early `→ AUTO_CLOSED`.
- **SLA by severity:** P1 `PT4H`, P2 `PT8H`, P3 `PT24H`, else `PT72H`. Regulatory timer: fixed `PT72H`.
- **13 workers (BPMN type):** `triage-threat, record-classification, auto-close, isolate-systems,
  collect-evidence, notify-stakeholders, restore-services, close-incident, generate-report, block-ip,
  revoke-credentials, deploy-patch, escalate`.
- **2 DMN:** `incident-classification`, `regulatory-notification` (both FIRST).
- **7 forms:** containment-verification, handle-isolation-failure, forensic-analysis, ciso-review,
  integrity-verification, file-regulatory-notification, incident-closure.
- **Companion docs:** `DESIGN-NOTE.md` (1–2 page), `RUN-WALKTHROUGH.md` (how to run, step-by-step),
  `DEMO-SCRIPT.md` (30-min walkthrough), `postman/` (API collection).
```
