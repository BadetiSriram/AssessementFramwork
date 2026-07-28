# Use Case 4 — Cyber Security Incident Response — Design Note

**Author:** Sriram Badeti · **Platform:** Camunda 8.9 SaaS · **Backend:** Spring Boot 4 on the
`camunda-process-framework` · **Module:** `incident-response-service`

## 1. Solution overview
A SIEM alert starts the `incident-response` process. AI triages the threat, a DMN classifies it P1–P4,
P1–P3 fan out into parallel Containment / Forensics / Notification streams plus an ad-hoc
response-actions stage the incident commander drives, the CISO authorises recovery, a DMN decides
whether regulatory notification is required (enforced by a 72-hour timer), AI drafts the
post-incident report, and the commander closes the incident. Every automated (blue) step is an
idempotent Spring Boot job worker on the base framework; every human (green) step is a Camunda
Tasklist user task with a form and candidate group; the two AI (purple) steps are connector calls;
the two decisions (orange) are DMN tables.

## 2. Processes & sub-processes — embedded vs call activity
Containment, Forensics and Recovery are modelled as **embedded sub-processes**, not call activities.
Rationale: they are **not reused** by any other process, they operate entirely on the parent
instance's variables/incident context (no independent lifecycle), and keeping them embedded ships the
whole flow as **one deployable, versioned unit** — avoiding the extra deployment/versioning and
variable-mapping overhead a call activity would add for no reuse benefit. The **isolation-failure
error boundary lives inside the Containment sub-process**, so containment failures are handled within
that scope. The P1–P3 fan-out uses a **parallel gateway** (split + join); "select only the tasks
needed" for response actions is a **true ad-hoc sub-process** (`activeElementsCollection`), not a
gateway chain.

## 3. DMN design
Two decision tables, both **hit policy FIRST**, invoked from business rule tasks
(`zeebe:calledDecision`, result mapped to a process variable):

- **Incident Classification** — inputs `attackConfirmed`, `assetCriticality`, `dataExposed`; output
  `severity` (P1–P4). Rules are ordered by precedence: false-positive first (→ P4, auto-closed), then
  most-severe downward. Severity drives the downstream SLA timer. The inputs are **request-driven**
  process variables (supplied on `POST /incidents`, defaulting to a high-severity P1); in production
  they would be populated by the AI triage / enrichment step rather than the caller.
- **Regulatory Notification Required** — inputs `dataExposed`, `recordCount`; output
  `regulatoryRequired` (boolean).

**Why FIRST over UNIQUE/COLLECT:** each decision needs a **single deterministic output** and the
rules are naturally an **ordered precedence list**; FIRST encodes that intent directly and tolerates
overlapping conditions without the strict non-overlap obligation UNIQUE imposes (which is brittle to
maintain as rules grow). COLLECT/RULE-ORDER don't apply (no aggregation/multi-hit needed). Inputs are
sourced from process/AI variables, so the tables stay versionable and unit-testable in isolation.

## 4. AI connector design
Both AI steps call **OpenAI** through the HTTP REST connector (`io.camunda:http-json:1`); auth is the
`{{secrets.OPENAI_API_TOKEN}}` cluster secret (configured in Console, never in the model).

- **Prompts use process variables.** Triage: a SOC-triage system prompt + user prompt built from
  `title` and `source`. Report: an incident-analyst system prompt + user prompt built from `title`,
  `severity`, and the `triageReport`.
- **Structured output mapping.** `resultExpression` maps `response.body.choices[1].message.content`
  (FEEL is 1-indexed) into `triageReport` / `postIncidentReport`.
- **Failure/timeout handling.** `retries=2`, an explicit read timeout, and an `errorExpression` that
  raises `bpmnError("AI_STEP_FAILED")`. An **error boundary falls back to the equivalent job worker**,
  so an AI outage/timeout never stalls the incident — the flow degrades gracefully and still
  completes. (Swappable for the dedicated OpenAI / AI-Agent connector template in Web Modeler.)

## 5. Error handling & resilience
The framework's **business-failure vs technical-failure** rule is applied deliberately:

- **Business/expected deviations → BPMN error.** Failed automated isolation returns
  `WorkResult.businessError("ISOLATION_FAILED")` → the framework throws a BPMN error caught by the
  in-scope boundary, escalating to the **incident commander** rather than retrying forever.
- **Technical/transient failures → retry then incident.** A `RetryableException` is rethrown so Zeebe
  decrements retries and eventually raises an **incident** in Operate for an operator.
- **Idempotency (re-delivery safe).** Every worker carries `businessKey = incidentId`; the framework's
  `IdempotencyGuard` short-circuits replays, and the actions are written to be naturally idempotent.
- **Timers & escalation (UC4 core).** A **severity-based, non-interrupting SLA timer** (`=slaDuration`,
  derived from severity) on CISO Review escalates if overdue; a **non-interrupting 72-hour timer** on
  the regulatory-notification task escalates to the CISO to enforce the legal deadline.
- **Compensation** is not required for UC4 (no monetary/irreversible saga step); the framework's
  `WorkResult.compensated()` pattern is available if a future step needs it.

## 6. Human tasks, personas & backend
Seven **native Camunda user tasks** (Handle Isolation Failure, Containment Verification, Forensic
Analysis, CISO Review, Integrity Verification, File Regulatory Notification, Incident Closure), each
with a **Camunda Form** and a **candidate group** matching the persona (`soc-analyst`,
`incident-commander`, `forensics-lead`, `ciso`, `legal-compliance`). The backend follows the
framework's **hexagonal layering** (ArchUnit-enforced): `web → application → infrastructure.camunda`,
with 13 idempotent `BaseWorker` workers, an `Incident` aggregate whose status is a validated
`AuditableEntity` state machine, and **PostgreSQL** persistence (Flyway). A task API
(`/incidents/{id}/tasks…`) lets tasks be completed programmatically and records each outcome to
`incident_task_outcomes`; the graded demo still completes tasks in Tasklist.

## 7. Testing evidence
- **Happy path (verified):** a P1 incident driven end-to-end to **CLOSED** — parallel Forensic
  Analysis + Containment Verification → CISO Review → Integrity Verification → File Regulatory
  Notification → Incident Closure — with all six task outcomes persisted in Postgres.
- **Exception paths (modelled, demonstrable in Operate):** failed isolation → BPMN-error escalation to
  the commander (`forceIsolationFailure=true`); P4 → auto-close; AI failure → worker fallback; and the
  SLA / 72-hour escalation timers.

## 8. Notes & assumptions
Job workers are runnable placeholders for the real integrations (SIEM/EDR/firewall/IAM/email). The AI
steps need `OPENAI_API_TOKEN` in Console to produce live text (otherwise the fallback runs). The BPMN
was authored independently and can be reconciled with the separately-prepared UC4 flow.
