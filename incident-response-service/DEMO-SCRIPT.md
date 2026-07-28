# Use Case 4 — 30-Minute Walkthrough (Demo Script)

A presenter's guide for the assessment walkthrough. Read it top-to-bottom; it tells you what to
show, roughly when, and what to say. The goal is to demo the incident-response flow end-to-end
**through Tasklist** and be ready for design trade-off questions.

---

## Before the clock starts (setup — do this ahead of time)
Have these open and ready so you don't lose time:
1. **Backend running** — `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`; confirm the log shows
   `Successfully applied 3 migrations`, `Deployed Processes / Decisions / Forms`, and the 13 workers
   registered. PostgreSQL 17 must be up.
2. **Camunda Console tabs**: **Web Modeler** (the `Incident Response (UC4)` project), **Operate**, and
   **Tasklist** — all pointing at your 8.9 SaaS cluster.
3. **The `OPENAI_API_TOKEN` secret** configured in Console (so the AI Agent steps produce real text;
   otherwise they gracefully fall back to a worker — mention this either way).
4. **Postman** with the collection imported, or a terminal for the `curl` calls.
5. A **psql** window on the `incident_response` database (to show persistence at the end).
6. Log in to Tasklist as a user who is in the candidate groups (`soc-analyst`, `incident-commander`,
   `forensics-lead`, `ciso`, `legal-compliance`) — or be ready to claim tasks regardless.

---

## Minute-by-minute plan (~30 min)

### 1. Framing the problem (0:00–0:03)
Explain the business case in a sentence or two: an enterprise SOC drowns in alerts; genuine incidents
need a coordinated response across containment, forensics and communications, and regulators impose a
**72-hour notification deadline**. The goal is one orchestrated journey that **triages with AI**,
**classifies severity with business rules**, runs **containment and forensics in parallel**, lets the
**incident commander invoke response actions dynamically**, and **enforces the regulatory decision**
before closure. Say clearly: *the backend is built on our base microservice framework; nothing was
scaffolded from scratch.*

### 2. Architecture in one breath (0:03–0:06)
Show the repository structure briefly and describe the shape:
- **Hexagonal layering** enforced by ArchUnit (web → application → infrastructure.camunda), the same
  rules the framework ships.
- **13 idempotent Spring Boot job workers** back every automated step; **two DMN tables** make the
  decisions; **two AI Agent connector steps** do triage and the post-incident report; **seven Tasklist
  forms** with candidate groups per persona; **PostgreSQL** holds the incident aggregate and the
  human-task outcomes.
- Mention the framework value: workers get **idempotency, error classification (business vs
  technical), metrics and MDC** for free; you only wrote `doWork`.

### 3. The model in Web Modeler (0:06–0:12)
Open `incident-response.bpmn` and walk the flow left-to-right, naming the Camunda capabilities as you
go (this is where most of the "modeling quality" marks live):
- **Start** on a SIEM alert → **AI Threat Triage** (purple AI Agent task) → **Record Triage** worker.
- **Classify Incident** — a **DMN business rule task** producing `severity` (P1–P4). Point out the
  **P4 gateway**: false positives are **auto-closed**.
- The **parallel gateway** fanning into four streams: **Containment** and **Forensics** (each an
  **embedded sub-process**), **Notification**, and the **ad-hoc sub-process** of response actions the
  **incident commander** activates at runtime (block IP / revoke credentials / deploy patch).
- Inside Containment, show the **error boundary** on *Isolate Systems*: a failed isolation raises a
  **BPMN error** that escalates to the commander rather than retrying forever.
- After the join: **CISO Review** (with a **severity-based SLA timer**), the **Recovery** sub-process,
  the **Regulatory Notification** DMN, and — when required — the **File Regulatory Notification** task
  carrying a **non-interrupting 72-hour timer** that escalates to the CISO.
- Finally the **AI Post-Incident Report** and **Incident Closure**.
Call out that the AI tasks and both DMN tasks each have **failure handling** (AI error boundary →
worker fallback; DMN feeds gateways).

### 4. The decisions (0:12–0:14)
Open the two DMN tables:
- **Incident Classification** — inputs `attackConfirmed`, `assetCriticality`, `dataExposed` → `severity`.
- **Regulatory Notification Required** — inputs `dataExposed`, `recordCount` → boolean.
Say **why hit policy FIRST**: each decision needs a single deterministic output and the rules are an
ordered precedence list (false-positive first, then most-severe downward), which is easier to maintain
than the strict non-overlap that UNIQUE demands.

### 5. Start an incident — the happy path (0:14–0:16)
Trigger a P1 incident (Postman **"Raise incident (P1)"**, or `POST /incidents`). Immediately switch to
**Operate** and open the new instance. Narrate what runs automatically:
- AI triage (or its fallback) → **RAISED → TRIAGED**;
- the classification DMN → **severity P1**; Record Classification → **CLASSIFIED**;
- the four parallel branches execute their workers and the instance now **waits at the human tasks**.
Point at the token positions and the process variables panel (`severity`, `triageReport`, `slaDuration`,
`attackConfirmed`, etc.).

### 6. Do the work in Tasklist (0:16–0:23)
This is the graded part — **complete the human tasks in Tasklist, not the API.** Show that each task
lands with the right **candidate group** and a **form**:
- **Containment Verification** (SOC Analyst) and **Forensic Analysis** (Forensics Lead) — the two
  parallel tasks. Fill the forms and complete.
- Back in Operate, show the parallel join releasing → **CISO Review** (CISO) appears; accept residual
  risk / authorize recovery.
- **Integrity Verification** (SOC) inside the Recovery sub-process.
- **File Regulatory Notification** (Legal/Compliance) — mention the 72-hour timer riding on it.
- **Incident Closure** (Incident Commander) — complete it; the `close-incident` worker fires and the
  instance **completes**. Show the incident is now **CLOSED** (Operate shows the finished instance;
  `GET /incidents/{id}` shows `status = CLOSED`).

### 7. Show an exception path (0:23–0:26)
Start a second incident with `forceIsolationFailure = true` (Postman **"Raise incident (isolation-
failure path)"**). In Operate, show automated isolation raising a **BPMN error** caught by the boundary,
which routes to the **Handle Isolation Failure** task for the **Incident Commander** — i.e. it escalates
to a human instead of silently retrying. Mention the other two exception paths you modelled: **P4 →
auto-close**, and the **72-hour timer** escalation on the regulatory task.

### 8. Prove persistence & the backend (0:26–0:28)
Switch to psql (or the API): show the `incidents` row moving through
`RAISED → TRIAGED → CLASSIFIED → RECOVERING → CLOSED`, and the **`incident_task_outcomes`** table with a
row per completed human task (who completed it and the submitted form data). This demonstrates the
backend isn't a black box — every decision and human action is recorded.

### 9. Close & invite questions (0:28–0:30)
Recap what was demonstrated against the rubric (below) and open for trade-off questions.

---

## Design trade-offs — be ready to answer these
The walkthrough is explicitly scored on explaining trade-offs. Have crisp answers ready:

- **Embedded sub-processes vs call activities?** Embedded — Containment/Forensics/Recovery aren't
  reused elsewhere, they share the incident's variables, and embedding keeps everything in one
  deployable, versioned unit. A call activity would add deployment/versioning and variable-mapping
  overhead for no reuse benefit.
- **DMN hit policy — why FIRST?** Single deterministic output, rules ordered by precedence; tolerant of
  overlapping conditions, unlike UNIQUE which is brittle to maintain as rules grow.
- **AI as a connector, and why the fallback?** The AI steps are **AI Agent Task** connectors (OpenAI);
  each has retries and an **error boundary that falls back to a job worker**, so an AI outage or a
  missing key never stalls an incident — resilience over hard dependency.
- **BPMN error vs incident?** Expected/business deviations (failed isolation) → a **BPMN error** that a
  human handles; unexpected/technical failures → a **retry then an Operate incident** for an operator.
  The framework encodes exactly this split.
- **Idempotency?** Every worker carries `businessKey = incidentId`; the framework's `IdempotencyGuard`
  makes SIEM re-delivery and job retries safe, and the actions themselves are written to be idempotent.
- **Ad-hoc sub-process vs gateways?** "Select only the actions needed" is genuine runtime selection by
  the commander — that's what an ad-hoc sub-process models; a gateway chain would hard-code the choice.
- **Timers?** A severity-derived SLA timer on CISO Review and a fixed 72-hour timer on regulatory
  notification, both **non-interrupting** so they escalate in parallel without cancelling the task.

## What to say if the AI step doesn't return live text
"The AI Agent task calls OpenAI using a cluster secret; if the secret isn't present or the call fails,
the error boundary routes to the fallback worker so the flow still completes — that's the resilience
pattern in action." (Then optionally configure the secret and re-run to show real output.)

## Rubric coverage checklist (mention at the close)
Process modeling + DI · two DMN tables · two AI connector steps · ad-hoc sub-process · idempotent
Spring Boot workers · error handling (BPMN errors, retries, escalation) · Tasklist forms + candidate
groups per persona · timers & escalation · testing evidence (happy path to CLOSED + the isolation-
failure exception).
