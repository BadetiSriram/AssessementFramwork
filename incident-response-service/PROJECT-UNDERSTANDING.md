# Why the project looks the way it does

Notes for explaining this to someone else. For each BPMN element and each bit of Java: what it is,
why it's there, how it works. There's an end-to-end scenario at the end, and a set of likely
questions.

Process id is `incident-response`, base package `com.aaseya.incident`, database is PostgreSQL 17
(`incident_response`). Built on `camunda-process-framework`, same as `order-service-sample`.

---

## 1. The problem

A SOC gets thousands of alerts a day. The runbooks live in a wiki, the response actions are done by
hand, and the 72-hour regulatory notification deadline gets tracked in somebody's inbox. Slow,
inconsistent, and one distraction away from a compliance failure.

What we want instead is a single auditable journey: AI triages the alert, business rules set the
severity, containment and forensics run at the same time, the commander picks response actions as
findings come in, the regulatory decision is forced before anyone can close anything, and a report
comes out the other end.

Time-bound response is the capability this use case is really about, so the timers and escalation
paths are modelled on purpose rather than bolted on.

---

## 2. The shape of it

Two cooperating halves:

On the Camunda side, BPMN orchestrates the flow, DMN makes the decisions, AI connectors do the
reasoning, Tasklist forms handle the human steps, and timers enforce the deadlines.

On our side, job workers do the automated work, the `Incident` aggregate holds state in Postgres,
and a couple of REST endpoints start incidents and complete tasks.

```
SIEM alert ──▶ [Spring Boot service: POST /incidents] ──▶ starts the process in Camunda
                                                             │
Camunda (Zeebe) drives the flow ──▶ calls back into ◀───────┘
   job workers (automated steps), DMN (decisions),
   AI connectors (triage/report), Tasklist (human tasks),
   timers (SLA + 72h)
```

The split is the whole point of using an orchestrator: the engine owns coordination - who runs
when, what waits, timers, retries, parallelism - so none of that has to be written in Java. Our
code only owns behaviour: state changes, persistence, and eventually the real integrations.

---

## 3. Stack choices

| Choice | Why we used it |
|---|---|
| Camunda 8.9 SaaS | The assessment platform; gives BPMN/DMN engine (Zeebe), Operate (monitoring), Tasklist (human tasks), Web Modeler, and connectors - all managed. |
| BPMN for the flow | Standard, visual, executable. The same diagram is the documentation *and* the running process - business and engineers read one artifact. |
| DMN for decisions | Rules (severity, regulatory) change often and must be readable/auditable by non-developers. A decision table is versionable and unit-testable; hard-coded `if/else` in Java is not (and the rubric marks it down). |
| AI Agent connector for triage/report | Natural-language reasoning (enrichment, summarising) is exactly what an LLM is good at and what rules/code are bad at. A connector keeps the credential and call out of our code. |
| Spring Boot 4 job workers | The rule: every automated service task is a Spring Boot worker. Keeps business logic in our tested, version-controlled service, not in the model. |
| Base framework (`camunda-process-framework`) | Gives idempotency, error classification, metrics, layering, and a state machine for free - we only write `doWork()`. (Section 5.) |
| PostgreSQL 17 | Durable store for the incident aggregate and the human-task outcomes; production-grade (we migrated off H2). |
| Flyway | Versioned, repeatable schema migrations (V1 framework tables, V2 incidents, V3 outcomes). |

---

## 4. What the framework gives us

Nothing here was scaffolded from scratch. The framework was a requirement, but it also does real
work:

| Framework building block | What it gives | Why UC4 needs it |
|---|---|---|
| `BaseWorker<V>` | A worker template - you implement `varsType()` + `doWork()`; it handles activation, variable mapping, metrics, MDC logging, error translation. | We wrote 13 workers with almost no boilerplate; all behave consistently. |
| `IdempotencyGuard` | Skips a job if the same `businessKey` was already processed (via a `worker_execution` table). | UC4 demands re-delivery-safe workers; a SIEM can fire the same alert twice, and Zeebe re-delivers jobs on retry. |
| `WorkResult` | `completed(vars)` / `businessError(code,msg)` / `compensated()` - the worker's verdict. | Lets a worker cleanly signal a business failure (failed isolation) vs a technical one. |
| `AuditableEntity<S>` | A guarded state machine base class + audit columns (created/updated by/at). | The `Incident` status can only move through legal transitions; illegal moves throw. Free audit trail. |
| `ProcessService` | A thin port to start processes / complete user tasks without importing the Camunda SDK in business code. | Keeps our application layer clean (see layering below). |
| 6 ArchUnit rules | Compile-time-ish tests that enforce hexagonal layering. | Guarantees workers live in `infrastructure.camunda`, only that package touches `io.camunda`, domain stays pure - the rubric grades "logic in the right place". |

The business-vs-technical split matters enough to repeat: `WorkResult.businessError(code, ...)`
becomes a BPMN error that a boundary event catches and the flow handles, which is for expected
deviations. Throwing a `RetryableException` makes Zeebe retry and eventually raise an incident in
Operate, which is for things that actually went wrong.

---

## 5. The BPMN, element by element

Grouped by phase. File is `src/main/resources/processes/incident-response.bpmn`.

### 5.1 Intake and classification

| Element | BPMN type | Why here / purpose | How it works | Key variables |
|---|---|---|---|---|
| SIEM alert (`StartEvent_1`) | Start event | The incident begins when an alert arrives. | Started by our service (`POST /incidents`) via `ProcessService.start()`. | `businessKey`, `incidentId`, `title`, `source`, triage signals |
| AI Threat Triage (`Task_TriageAI`) | AI Agent connector task | UC4 requires an AI triage step; summarising/enriching an alert is an LLM strength. | Calls OpenAI `gpt-4o-mini`; writes a triage summary. On error → BPMN error `AI_STEP_FAILED`. | writes `triageReport` |
| AI failed (`Boundary_TriageAiFailed`) | Error boundary event | Resilience - an AI outage or missing key must never stall an incident. | Catches `AI_STEP_FAILED`, routes to the worker fallback. | - |
| Record Triage (`Task_Triage`) | Job worker `triage-threat` | Advance the domain state to TRIAGED; also the fallback target if the AI step failed. | `TriageWorker` → `incidentService.markTriaged()`. | - (signals come from start) |
| Classify Incident (`Task_Classify`) | DMN business rule task | UC4 requires severity classification via rules, not code. | Calls DMN `incident-classification`; result → `severity`. | reads `attackConfirmed/assetCriticality/dataExposed`, writes `severity` |
| Record Classification (`Task_RecordClass`) | Job worker `record-classification` | Persist severity and derive the SLA the timers will use. | `RecordClassificationWorker` → status CLASSIFIED; maps P1→PT4H, P2→PT8H, P3→PT24H. | writes `slaDuration` |
| P4 / false positive? (`Gateway_P4`) | Exclusive gateway | Not every alert is real - false positives must auto-close, not consume the full response. | If `severity="P4"` → auto-close branch; else → parallel response. | reads `severity` |
| Auto Close (P4) (`Task_AutoClose`) | Job worker `auto-close` | Cleanly close a false positive with an audit note. | `AutoCloseWorker` → status AUTO_CLOSED → end. | - |

The AI, worker and DMN ordering is deliberate. An LLM is good at prose and bad at being
deterministic; a decision table needs structured facts and has to give the same answer twice. So
the LLM writes `triageReport` and nothing else, and the four signals the DMN reads come in as
process variables on `POST /incidents`. In production the enrichment step would set them. Keeping
the two apart means the classification stays reproducible, and it happens to make every severity
path demoable on request.

### 5.2 Parallel response

| Element | BPMN type | Why here / purpose | How it works |
|---|---|---|---|
| Response streams (`Gateway_Split`) | Parallel gateway (split) | Containment, forensics, notification and dynamic actions are independent and time-critical - running them in sequence would waste precious minutes. | Forks 4 concurrent branches. |
| Containment (`SubProcess_Containment`) | Embedded sub-process | Groups the containment steps into one logical, collapsible unit that shares the incident's variables. | Isolate → (on failure, escalate) → SOC verification. |
| Forensics (`SubProcess_Forensics`) | Embedded sub-process | Groups evidence collection + analysis. | Collect evidence → forensic analysis (human). |
| Notify Stakeholders (`Task_Notify`) | Job worker `notify-stakeholders` | Automated stakeholder comms - a single automated step, so a plain worker. | Sends notifications. |
| Response Actions (ad-hoc) (`AdHoc_ResponseActions`) | Ad-hoc sub-process | UC4 rule #6: the commander selects only the actions needed, at runtime - that is exactly an ad-hoc sub-process, not a gateway chain. | Activates the inner tasks named in `responseActions`; more can be activated (and repeated) at runtime. |
| Join (`Gateway_Join`) | Parallel gateway (join) | Recovery must not start until all four streams finish. | Waits for all 4 branches. |

Inside Containment, which is where most of the error handling lives:

| Element | Type | Why here | How it works |
|---|---|---|---|
| Isolate Systems (`Task_Isolate`) | Job worker `isolate-systems` | Automated network/host isolation. | `IsolationWorker`; if it fails → `businessError("ISOLATION_FAILED")`. |
| Isolation failed (`Boundary_IsolationFailed`) | Error boundary event | UC4: *"failed isolation raises a BPMN error that escalates to the incident commander rather than silently retrying forever."* | Catches `ISOLATION_FAILED`, routes to a human task. |
| Handle Isolation Failure (`Task_HandleIsoFail`) | User task (incident-commander) | A human must contain manually when automation can't. | Tasklist form `handle-isolation-failure-form`; then flows to verification. |
| Containment Verification (`Task_ContainVerify`) | User task (soc-analyst) | A person confirms containment actually worked. | Tasklist form; ends the sub-process. |

Embedded rather than call activities because nothing else reuses these three, they work on the
incident's own variables, and embedding ships them as one versioned unit. A call activity would buy
independent versioning we have no use for and cost variable mapping we'd have to maintain.

Ad-hoc for the response actions because the commander should be able to compose and repeat only the
counter-measures this particular incident needs, rather than every incident walking a path someone
drew in advance. The inner tasks have no gateways between them on purpose - a parallel join inside
an ad-hoc sub-process is broken in 8.9.

### 5.3 Review, recovery, regulatory deadline

| Element | BPMN type | Why here / purpose | How it works | Variables |
|---|---|---|---|---|
| CISO Review (`Task_CisoReview`) | User task (ciso) | A manager must accept residual risk and authorize recovery. | Tasklist form `ciso-review-form`. | - |
| SLA breach (`Boundary_CisoSla`) | Non-interrupting timer boundary | Severity-based SLA on a key human task (UC4 CORE). | `timeDuration = =slaDuration`; doesn't cancel the task, escalates in parallel. | reads `slaDuration` |
| Escalate SLA Breach (`Task_EscalateSla`) | Job worker `escalate` | Someone gets paged if the CISO is slow - the task itself continues. | `EscalationWorker` → separate end event. | - |
| Recovery (`SubProcess_Recovery`) | Embedded sub-process | Group restore + integrity check. | Restore services → integrity verification (human). | status → RECOVERING |
| Restore Services (`Task_Restore`) | Job worker `restore-services` | Bring systems back from a clean state. | `RestoreServicesWorker` → status RECOVERING. | - |
| Integrity Verification (`Task_IntegrityVerify`) | User task (soc-analyst) | A person verifies restored systems are actually clean. | Tasklist form `integrity-verification-form`. | - |
| Regulatory Notification Required? (`Task_RegDecision`) | DMN business rule task | Whether we must notify regulators is a rule (data categories, record counts), not code. | Calls DMN `regulatory-notification`. | writes `regulatoryRequired` |
| Regulatory required? (`Gateway_Reg`) | Exclusive gateway | Only file notices when the rule says so. | `true` → file notification; else skip. | reads `regulatoryRequired` |
| File Regulatory Notification (`Task_FileRegNotification`) | User task (legal-compliance) | Legal files the notices. | Tasklist form `file-regulatory-notification-form`. | - |
| 72h deadline (`Boundary_Reg72h`) | Non-interrupting timer boundary | The headline UC4 requirement - the 72-hour regulatory deadline with escalation. | `timeDuration = =regulatoryDeadline` (`PT72H` unless the raise body shortens it); escalates to the CISO without cancelling the filing task. | reads `regulatoryDeadline` |
| Escalate to CISO (72h) (`Task_EscalateCiso`) | Job worker `escalate` | If the deadline is at risk, the CISO is alerted. | `EscalationWorker` → separate end event. | - |
| Regulatory merge (`Gateway_RegMerge`) | Exclusive gateway | Rejoin the "required" and "not required" paths. | Both paths continue to the report. | - |
| AI Post-Incident Report (`Task_ReportAI`) | AI Agent connector task | UC4 requires an AI report (timeline, root cause, impact, lessons). | OpenAI; on error → `AI_STEP_FAILED`. | writes `postIncidentReport` |
| AI failed (`Boundary_ReportAiFailed`) | Error boundary event | Same resilience pattern as triage. | Routes to the worker fallback. | - |
| Generate Report (fallback) (`Task_Report`) | Job worker `generate-report` | Guarantees a report exists even if the AI is down. | `PostIncidentReportWorker` writes a placeholder. | writes `postIncidentReport` |
| Incident Closure (`Task_Closure`) | User task (incident-commander) | The commander reviews the report and makes the final call. | Tasklist form `incident-closure-form`. | - |
| Close Incident (`Task_Close`) | Job worker `close-incident` | Persist the terminal state. | `CloseIncidentWorker` → status CLOSED → end. | - |

Both timers are non-interrupting (`cancelActivity="false"`). An SLA breach means somebody should
be told, not that the CISO's half-finished review should be thrown away. Interrupting timers would
cancel the task and lose the work; non-interrupting ones escalate alongside it. That's what a
deadline reminder actually means.

The two error types (`ISOLATION_FAILED`, `AI_STEP_FAILED`) are both expected deviations with a
defined recovery in the flow, so they're BPMN errors caught by boundary events rather than
technical failures left to surface as incidents.

---

## 6. The decisions

Two tables, both **hit policy FIRST**, invoked from business rule tasks (`zeebe:calledDecision`).

**1) Incident Classification** - inputs `attackConfirmed`, `assetCriticality`, `dataExposed`; output
`severity` (P1-P4). Rules are ordered by precedence: false-positive first (→ P4), then most-severe
downward. `severity` then drives the SLA timer.

**2) Regulatory Notification Required** - inputs `dataExposed`, `recordCount`; output
`regulatoryRequired` (boolean).

FIRST because both tables are an ordered precedence list and need exactly one answer. FIRST says
that directly and copes with rules that overlap; UNIQUE forbids overlap, which is fine with six
rules and painful with twenty. COLLECT and RULE ORDER don't apply - there's nothing to aggregate.
Inputs come from process variables, so the tables can be versioned and tested on their own.

---

## 7. The AI steps

Both AI steps use the **AI Agent Task** element template (`io.camunda.agenticai:aiagent:1`,
template `io.camunda.connectors.agenticai.aiagent.v1`), provider **OpenAI `gpt-4o-mini`**, auth via
the cluster secret **`{{secrets.OPENAI_API_TOKEN}}`**.

A system prompt sets the role ("SOC threat-triage assistant", "incident-response analyst") and the
shape of the answer; the user prompt injects process variables - `title` and `source` for triage,
`title`, `severity` and `triageReport` for the report. `resultExpression` drops the text into a
clean variable rather than leaving the whole connector response lying around.

The important part is the failure handling. `errorExpression` turns any connector failure into
`AI_STEP_FAILED`, and the boundary event routes to a job-worker fallback. A missing key, a timeout
or an OpenAI outage costs you the quality of the write-up, not the incident.

A connector rather than a worker because it keeps the API credential and the HTTP plumbing out of
our codebase and configurable in the model.

---

## 8. The Java, layer by layer

Hexagonal, with six ArchUnit rules holding it in place. Dependencies point web → application →
infrastructure, and the domain depends on nothing.

### 8.1 Domain (`com.aaseya.incident.domain`)
`Incident` is the aggregate. It extends `AuditableEntity<IncidentStatus>`, so status changes go
through `allowedTransitions()` and an illegal move throws instead of quietly corrupting the record.
That's the aggregate defending its own invariants - no service can talk it into a bad state. It also
carries `severity`, `businessKey` and `processInstanceKey`.

`IncidentStatus` and `IncidentSeverity` are the vocabulary, persisted as strings so a reordering
never silently remaps existing rows.

`IncidentTaskOutcome` is the audit trail: who completed which element, and what they submitted.

The domain deliberately has no Spring Web or `io.camunda` imports, which keeps it testable without
an engine and is the one rule most worth enforcing.

### 8.2 Application (`…application`)
- **`IncidentService`** - the use-case layer. `raiseIncident()` creates the aggregate, saves it, and
  **starts the process** (via `ProcessService`), storing the returned **process instance key**. It
  builds the **start variables** (including the request-driven triage signals with defaults). The
  worker-callbacks (`markTriaged`, `recordClassification`, `markRecovering`, `close`, `autoClose`)
  each load the aggregate and call a domain method. **Transactions live here** (ArchUnit rule 4 -
  never on the controller).
- **`IncidentTaskService`** - lists an incident's active Tasklist tasks, **completes** them
  (submitting form variables to Camunda), and **persists each outcome**. Enforces "one active task"
  vs "complete by key".
The application layer earns its keep by coordinating domain, persistence and engine without any of
those leaking into each other, and by being the obvious home for the transaction boundary.

### 8.3 Infrastructure / Camunda (`…infrastructure.camunda`)
- **13 job workers** (all `BaseWorker` subclasses, all here per ArchUnit rule 2). Each declares a
  `@JobWorker(type="…")` whose **type must exactly match** the BPMN `zeebe:taskDefinition type`, a
  typed `varsType()` (a record of only the variables it needs), and a `doWork()` returning a
  `WorkResult`. Representative examples:
  - **`IsolationWorker`** - returns `businessError("ISOLATION_FAILED")` on failure (drives the error
    boundary); otherwise idempotent success.
  - **`RecordClassificationWorker`** - persists severity and **derives `slaDuration`** for the timers.
  - **`TriageWorker`** - advances domain state only (signals now come from the request).
- **`CamundaTaskAdapter`** - the **only** place that talks to the Camunda client for user-task
  search/complete (Orchestration Cluster REST v2). Keeps the SDK out of the application layer.
- **`CamundaDeploymentConfig`** - `@Deployment(resources = {processes/*.bpmn, dmn/*.dmn,
  forms/*.form})` auto-deploys the model on startup (the framework has no auto-deploy; we wired it).
Every worker passes `businessKey = incidentId` so the guard can short-circuit a duplicate, and the
actions are written to be repeatable anyway - belt and braces, because a SIEM firing the same alert
twice is normal, not exceptional.

### 8.4 Web (`…web`)
- **`IncidentController`** - `POST /incidents` (raise, returns 201 + the incident) and `GET
  /incidents/{id}`. DTO-only, non-transactional (ArchUnit rules 4 & 5).
- `IncidentTaskController` - `GET …/tasks`, `POST …/tasks/{key}/complete`, `POST …/tasks/complete`,
  `GET …/tasks/outcomes`. Mainly for scripting and tests; the demo itself uses Tasklist.
- **DTOs** (`RaiseIncidentRequest`, `IncidentDto`, `UserTaskView`, `CompleteTaskRequest`,
  `TaskOutcomeView`) - keep the domain out of the HTTP boundary.

### 8.5 Config & persistence
- **`application-local.yml`** - Camunda SaaS credentials + Postgres. **Git-ignored** (secrets); only
  the `.example` with placeholders is committed.
- **Flyway migrations** - `V1` (framework tables incl. `worker_execution` for idempotency), `V2`
  (`incidents`), `V3` (`process_instance_key` + `incident_task_outcomes`). `ddl-auto=validate` - the
  schema is owned by migrations, Hibernate only validates.

### 8.6 The ArchUnit rules

Workers only under `infrastructure.camunda`, only that package importing `io.camunda.client`, domain
stays pure, transactions only in the application layer, controllers thin, constructor injection
throughout. The value is that the layering is checked on every build rather than being an intention
someone violates in a hurry six months from now.

---

## 9. What we persist

| Table | Why it exists | Key columns |
|---|---|---|
| `incidents` | The incident aggregate - the business record and its lifecycle. | `id`, `business_key`, `title`, `source`, `severity`, `status`, `process_instance_key`, audit columns |
| `incident_task_outcomes` | Proof of *who did what* - every human-task completion (persona action + submitted form data). | `incident_id`, `user_task_key`, `element_id`, `task_name`, `completed_by`, `outcome` (JSON), `created_at` |
| `worker_execution` (framework) | Backs the idempotency guard (dedupes job re-delivery). | `business_key`, worker id, status |

Outcomes live in our own table rather than relying on Camunda history, which expires. For an
incident record the human decisions are exactly the part you want to still have in a year.

---

## 10. Trade-offs

| Decision | We chose | Over | Why |
|---|---|---|---|
| Sub-process style | Embedded | Call activity | No reuse; shared variables; one versioned deployable. |
| Response actions | Ad-hoc sub-process | Gateway chain | Genuine runtime, repeatable selection by the commander. |
| Severity/regulatory rules | DMN (FIRST) | Java `if/else` | Auditable, versionable, testable; rubric requires it. |
| AI steps | Connector + boundary fallback | Worker calling an API / no fallback | Keeps creds out of code; never stalls on AI failure. |
| Isolation failure | BPMN error → human | Infinite retry / incident | Expected deviation with a human recovery, per UC4. |
| Timers | Non-interrupting | Interrupting | Escalate in parallel without discarding in-progress work. |
| Idempotency | `businessKey` + guard + naturally idempotent actions | Nothing | SIEM re-delivery and job retries must be safe. |
| Triage → DMN inputs | Request-driven variables (defaults) | Hardcoded in the worker | Deterministic, lets us demo P1-P4; logic still lives in DMN. |
| Deployment | `@Deployment` on startup | Manual upload | One command boots and deploys everything reproducibly. |

---

## 11. End-to-end scenarios

Assumes the service is running, Postgres is up, and Operate and Tasklist are open.

### A. Happy path: a real P1, driven to CLOSED
1. **Alert arrives.** `POST /incidents {"title":"Ransomware + exfiltration on prod-db","source":"SIEM"}`.
   The service saves the incident (**status RAISED**) and starts the process; the **process instance
   key** is stored on the incident.
2. **AI triage** runs (`Task_TriageAI`) and writes `triageReport`. (If OpenAI is unavailable, the
   **error boundary** falls back to the `triage-threat` worker - the flow continues either way.)
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

### B. Isolation fails
`POST /incidents {"title":"…","source":"SIEM","forceIsolationFailure":true}`. The `isolate-systems`
worker returns `businessError("ISOLATION_FAILED")` → the **error boundary** catches it → **Handle
Isolation Failure** task appears for the **Incident Commander** (manual containment) → then normal
Containment Verification. The point being that a failed automated action goes to a human instead of
retrying forever against something that is never going to work.

### C. False positive
`POST /incidents {"title":"Benign alert","source":"SIEM","attackConfirmed":false}`. The DMN returns
**P4** → the **P4 gateway** routes to **Auto Close** → **status AUTO_CLOSED**, no parallel response,
no human tasks. Noise gets closed cheaply, which is the only way a SOC survives its own alert
volume.

### D. The 72-hour deadline passes
On the regulatory path, if **File Regulatory Notification** isn't completed within **PT72H**, the
**non-interrupting timer** fires **Escalate to CISO** in parallel. The filing task stays open, but
somebody now knows the deadline is at risk - enforced by the engine rather than by whoever
remembered to set a calendar reminder.

Shorten the timers (PT2M or so) if you want to show this firing live in a demo.

---

## 12. Rubric mapping

| Rubric criterion (weight) | Where we satisfy it |
|---|---|
| Process modeling quality (20%) | Clean BPMN, correct gateway/sub-process choices, meaningful names, full DI. |
| DMN design (15%) | Two tables, justified FIRST hit policy, inputs from process variables, versionable. |
| AI Connector usage (15%) | Two AI Agent steps, prompts from variables, output mapped to variables, failure handling via boundary→fallback. |
| Ad-hoc sub-process (10%) | Runtime, repeatable action selection by the commander. |
| Spring Boot workers (20%) | Framework layering, 13 idempotent workers, correct types/retries, logic in the right layer (ArchUnit). |
| Error handling & resilience (10%) | BPMN error vs incident used deliberately; bounded retries; escalation. |
| Tasklist & personas (5%) | 7 forms, candidate groups per persona, scoped task variables. |
| Testing evidence & docs (5%) | Happy path to CLOSED + exception paths; this doc + DESIGN-NOTE + RUN-WALKTHROUGH. |

---

## 13. Questions to expect

- **"Why Camunda and not just code?"** - Coordination (waiting, timers, parallelism, retries, human
  tasks, monitoring) is complex and cross-cutting; the engine gives it for free and makes the flow
  visible/auditable. We keep business logic in tested Java workers.
- **"Why embedded sub-processes?"** - No reuse, shared variables, one versioned deployable; call
  activities would add overhead for no benefit.
- **"Why an ad-hoc sub-process?"** - The commander selects only the actions needed, at runtime, and
  can repeat them - a gateway chain would hard-code the choice.
- **"Why DMN FIRST?"** - Single deterministic output over an ordered precedence list; tolerant of
  overlap; maintainable and unit-testable.
- **"What if the AI is down?"** - Error boundary falls back to a worker; the incident still closes.
- **"BPMN error vs incident?"** - Expected deviations (failed isolation) → BPMN error → human;
  technical faults → retry → Operate incident.
- **"Is it idempotent / re-delivery safe?"** - Yes: `businessKey` + `IdempotencyGuard` + naturally
  idempotent actions.
- **"Why non-interrupting timers?"** - Escalate in parallel without cancelling legitimate in-progress
  work.
- **"Where are the human tasks done?"** - Tasklist UI in the demo (candidate groups per persona); the
  REST task API exists only for testing/automation.
- **"Aren't those triage values hardcoded?"** - They're input data with defaults, not decision
  logic. The classification itself lives entirely in the DMN; the request only supplies the facts.

---

## 14. Quick reference

- **Personas → candidate groups:** SOC Analyst `soc-analyst`, Incident Commander `incident-commander`,
  Forensics Lead `forensics-lead`, CISO `ciso`, Legal/Compliance `legal-compliance`.
- **Status machine:** `RAISED → TRIAGED → CLASSIFIED → RECOVERING → CLOSED`; early `→ AUTO_CLOSED`.
- **SLA by severity:** P1 `PT4H`, P2 `PT8H`, P3 `PT24H`, else `PT72H`. Regulatory timer: `PT72H`.
  Both read process variables (`slaDuration`, `regulatoryDeadline`), so the raise body can shorten
  them to seconds for a demo.
- **13 workers (BPMN type):** `triage-threat, record-classification, auto-close, isolate-systems,
  collect-evidence, notify-stakeholders, restore-services, close-incident, generate-report, block-ip,
  revoke-credentials, deploy-patch, escalate`.
- **2 DMN:** `incident-classification`, `regulatory-notification` (both FIRST).
- **7 forms:** containment-verification, handle-isolation-failure, forensic-analysis, ciso-review,
  integrity-verification, file-regulatory-notification, incident-closure.
- **Other docs:** `DESIGN-NOTE.md`, `RUN-WALKTHROUGH.md`, `DEMO-SCRIPT.md`, `CHEAT-SHEET.md`, and
  the Postman collection under `postman/`.
```
