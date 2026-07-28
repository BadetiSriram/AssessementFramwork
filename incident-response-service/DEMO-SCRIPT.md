# 30-minute walkthrough

Presenter's notes. Read top to bottom; it says what to show, roughly when, and what to say. The
aim is to get an incident from SIEM alert to CLOSED through Tasklist, and to have answers ready
for the trade-off questions.

---

## Setup (do this before the clock starts)

Get all of this open ahead of time so you're not fumbling:

1. Backend running - `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`. Check the log for
   3 migrations, the deployments, and 13 workers. Postgres has to be up first.
2. Console tabs: Web Modeler on the Incident Response project, Operate, and Tasklist, all against
   the 8.9 SaaS cluster.
3. `OPENAI_API_TOKEN` set as a cluster secret, so the AI steps return real text. Worth mentioning
   the fallback either way.
4. Postman with the collection imported, or a terminal.
5. A psql window on `incident_response` for the persistence bit at the end.
6. Tasklist logged in as someone in the candidate groups, or be ready to just claim tasks.

---

## The plan

### 0:00-0:03 Framing

A SOC drowns in alerts. The real incidents need containment, forensics and comms happening at once,
and regulators want to hear about a breach inside 72 hours. So: one orchestrated journey that
triages with AI, classifies with business rules, runs containment and forensics in parallel, lets
the commander pick response actions as he goes, and forces the regulatory decision before anyone
can close the incident.

Worth saying up front that the backend sits on our base microservice framework - nothing was
scaffolded from scratch.

### 0:03-0:06 Architecture

Show the repo structure and describe the shape: hexagonal layering with ArchUnit enforcing it, 13
idempotent job workers behind the automated steps, two DMN tables, two AI Agent steps, seven forms
with a candidate group each, Postgres for the aggregate and the task outcomes.

The framework point is worth making: workers get idempotency, business-vs-technical error
classification, metrics and MDC for free. All that was written here is `doWork`.

### 0:06-0:12 The model

Open the BPMN and walk it left to right. Most of the modelling marks live here.

Start on a SIEM alert, AI Threat Triage, Record Triage. Then Classify Incident - a DMN task that
produces `severity`. Point at the P4 gateway: false positives auto-close and never reach a human.

The parallel gateway fans into four: Containment and Forensics as embedded sub-processes,
Notification, and the ad-hoc sub-process the commander drives at runtime.

Drill into Containment and show the error boundary on Isolate Systems. A failed isolation raises a
BPMN error and escalates to the commander instead of retrying forever - that's a deliberate choice,
not a limitation.

After the join: CISO Review with its severity-based SLA timer, the Recovery sub-process, the
regulatory DMN, and when it says yes, the Legal task with a non-interrupting 72-hour timer hanging
off it. Then the AI report and closure.

Also worth showing the pool lanes - each human lane maps to the candidate group on the tasks
inside it, so the hand-offs are visible without reading any XML.

### 0:12-0:14 The decisions

Open both tables. Classification takes `attackConfirmed`, `assetCriticality`, `dataExposed` and
returns severity; the regulatory one takes `dataExposed` and `recordCount` and returns a boolean.

Why FIRST: each needs one deterministic answer and the rules are an ordered precedence list.
UNIQUE would demand the rules never overlap, which gets brittle the moment anyone adds a row.

### 0:14-0:16 Start a P1

Fire the P1 request, then switch straight to Operate and open the instance. Narrate what runs on
its own: AI triage takes it to TRIAGED, the DMN sets P1, Record Classification moves it to
CLASSIFIED, the four branches run their workers, and it parks at the human tasks.

Point at the token positions and the variables panel - `severity`, `triageReport`, `slaDuration`.

### 0:16-0:23 Do the work in Tasklist

This is the graded part, so use Tasklist rather than the API. Show that each task arrives with the
right group and a real form.

Containment Verification (SOC analyst) and Forensic Analysis (forensics lead) are both open at
once. Fill in the forms and complete them. Back in Operate you can watch the join release and CISO
Review appear - accept residual risk and authorise recovery.

Then Integrity Verification inside Recovery, File Regulatory Notification with the 72-hour timer
riding on it, and finally Incident Closure. Completing that lets `close-incident` fire and the
instance finishes. `GET /incidents/{id}` now shows CLOSED.

### 0:23-0:26 An exception path

Raise a second incident with `forceIsolationFailure: true`. In Operate, show isolation raising the
BPMN error, the boundary catching it, and Handle Isolation Failure landing with the commander. The
line to use: it escalates to a human rather than silently retrying.

Mention the other two you modelled - P4 auto-close, and the 72-hour escalation.

### 0:26-0:28 Persistence

Switch to psql. Show the `incidents` row having walked RAISED to CLOSED, and `incident_task_outcomes`
with a row per human task: who did it and what they submitted. The point is that the backend isn't
a black box; every decision and every human action is on record.

### 0:28-0:30 Wrap up

Recap against the rubric and take questions.

---

## Trade-off questions to have answers for

**Embedded sub-processes or call activities?** Embedded. Containment, Forensics and Recovery aren't
reused anywhere, they work on the incident's own variables, and embedding ships everything as one
versioned unit. A call activity buys independent versioning we'd never use, and costs variable
mapping we'd have to maintain.

**Why hit policy FIRST?** One deterministic answer, rules ordered by precedence. FIRST tolerates
overlapping conditions; UNIQUE forbids them, which is fine now and painful later.

**Why AI through a connector, and why a fallback?** They're AI Agent tasks with retries and an
error boundary that drops to a job worker. An OpenAI outage or a missing key degrades the write-up,
it doesn't stall an incident. Resilience over a hard dependency.

**BPMN error or Operate incident?** Business deviations that a human should decide on - failed
isolation - are BPMN errors. Technical failures retry and then raise an incident for an operator.
The framework already draws that line; we just used it.

**Idempotency?** Every worker carries `businessKey = incidentId` and the guard short-circuits
replays, so SIEM redelivery and job retries are safe. The actions are written to be naturally
idempotent too.

**Ad-hoc sub-process or gateways?** "Pick only the actions you need" is genuine runtime selection.
That's exactly what ad-hoc models. A gateway chain would mean guessing every combination up front.

**Timers?** Severity-derived SLA on CISO Review, fixed 72 hours on the regulatory task. Both
non-interrupting, so escalating doesn't cancel the task someone is in the middle of.

## If the AI step doesn't return live text

"The AI task calls OpenAI through a cluster secret. If the secret isn't there or the call fails,
the error boundary routes to the fallback worker and the flow still completes - which is the
resilience pattern doing its job." Then configure the secret and re-run if there's time.

## Rubric checklist

Process modelling and DI, two DMN tables, two AI connector steps, ad-hoc sub-process, idempotent
workers, error handling, forms and candidate groups per persona, timers and escalation, and testing
evidence: happy path to CLOSED plus the isolation-failure exception.
