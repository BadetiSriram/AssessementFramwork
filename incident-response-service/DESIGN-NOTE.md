# Design note: Cyber Security Incident Response

Sriram Badeti · Camunda 8.9 SaaS · Spring Boot 4 on `camunda-process-framework`

## 1. What the process does

A SIEM alert starts `incident-response`. AI triages the threat, a DMN classifies it P1-P4. P4 is a
false positive and auto-closes. P1-P3 fan out into parallel containment, forensics and notification
streams, alongside an ad-hoc stage where the incident commander picks response actions as findings
come in. The CISO authorises recovery, a second DMN decides whether Legal has to file (with a
72-hour timer on that task), AI drafts the post-incident report, and the commander closes it out.

Automated steps are idempotent job workers, human steps are native user tasks with forms and
candidate groups, and the two AI steps are connector tasks.

## 1a. The human steps carry the incident file forward

Each of the seven forms opens with a read-only briefing panel — an `html` component using feelers
templating over the process variables — showing everything the process knows at that point. The CISO
sees the forensic findings, the containment outcome and the actions that ran; the analyst verifying
integrity sees the recovery conditions the CISO imposed; the commander closing the incident sees the
post-incident report in full. Nothing in a briefing panel is bound to a `key`, so displaying a value
can never overwrite it.

Two consequences worth being explicit about:

- **Forensic findings drive the regulatory decision.** Two `expression` components on the forensic
  form recompute `dataExposed` and `recordCount` on submit, so the regulatory DMN is evaluated
  against the analyst's confirmed exposure rather than the SIEM's opening estimate. Both are written
  to fall through to the existing value when the new fields are left empty, so a caller that submits
  only the original fields gets exactly the old behaviour.
- **Sign-off checkboxes are `required`.** `containmentVerified`, `recoveryAuthorized`,
  `integrityVerified`, `notificationFiled` and `lessonsLearnedCaptured` all gate a forward-only path
  — the model has no "rejected" branch for them. Making them required means the form refuses to
  submit rather than recording a "no" the process would then ignore. Declining means leaving the task
  open, which is what the model actually supports.

Note that Camunda renders forms with **feelin** (the JavaScript FEEL engine), not the Scala engine
used server-side. The two have different built-in function sets — `get or else()` works in a BPMN
expression but not in a form template, so the forms use `if x = null then … else x` throughout.

## 2. Embedded sub-processes, not call activities

Containment, Forensics and Recovery are embedded. Nothing else reuses them, they work purely on the
parent instance's variables, and they have no lifecycle of their own, so a call activity would buy
independent versioning and variable mapping we have no use for, at the cost of shipping and
deploying separate models.

Keeping Containment embedded also means the isolation-failure error boundary sits inside that
scope, which is where it belongs: a containment failure is a containment problem, not a
whole-process problem.

The P1-P3 fan-out is a plain parallel gateway. The "pick only the actions you need" part is a real
ad-hoc sub-process driven by `activeElementsCollection`, rather than a chain of gateways guessing
at combinations up front.

## 3. Decisions

Two tables, both hit policy FIRST, both called from business rule tasks via `zeebe:calledDecision`.

**Incident Classification** takes `attackConfirmed`, `assetCriticality` and `dataExposed`, and
returns `severity`. Rules are ordered by precedence: rule out false positives, then work down from
most severe, with a catch-all at the bottom. Severity then feeds the SLA timer.

**Regulatory Notification Required** takes `dataExposed` and `recordCount` and returns a boolean.
A production version would also weigh data categories and jurisdictions.

FIRST rather than UNIQUE because both tables are naturally an ordered precedence list, and FIRST
says exactly that. UNIQUE would force the rules never to overlap, which is fine today and painful
the moment someone adds a row. COLLECT doesn't apply, since we want one answer, not an aggregate.

The DMN inputs arrive as process variables set at `POST /incidents`. In production the AI triage
step would populate them; letting the caller send them means any severity path can be demonstrated
on demand.

## 4. AI steps

Both use the AI Agent connector (`io.camunda.agenticai:aiagent:1`) against `gpt-4o-mini`, with the
key coming from the `OPENAI_API_TOKEN` cluster secret rather than the model.

Prompts are built from process variables. Triage gets a SOC-triage system prompt plus `title` and
`source`. The report prompt is a full dossier: the incident header, then every human finding the
process collected — the forensic characterisation (attack vector, MITRE technique, patient zero,
root cause, scope, records, dwell time, exfiltration status, data categories, IOCs), the containment
outcome and its verification checks, the CISO's residual-risk decision and recovery conditions, the
integrity result, and the regulatory position. Results are mapped back through `resultExpression`
into `triageReport` and `postIncidentReport`.

Every operand in that prompt is wrapped in an `if x = null then "…" else x` guard. This matters more
than it looks: FEEL returns `null` for a missing variable and `"text" + null` is also `null`, so one
absent field in a bare concatenation would blank the entire prompt, the connector would reject it,
and the failure would surface as a *spurious* `AI_STEP_FAILED` boundary firing. The guards mean an
unrecorded field reads as "not recorded" in the report — which is itself a finding about the
response.

Each task sets `retries=3` and an `errorExpression` that raises `bpmnError("AI_STEP_FAILED")`. An
error boundary event then routes to the equivalent job worker. The point is that an AI outage
degrades the quality of the write-up, not the outcome of the incident.

## 5. Error handling

The framework's split between business and technical failure is the organising idea.

Failed isolation is a *business* outcome, not a bug: `WorkResult.businessError("ISOLATION_FAILED")`
becomes a BPMN error, the in-scope boundary catches it, and a human decides what to do. Retrying a
firewall call that is never going to succeed just delays that decision.

Technical failures rethrow, Zeebe burns a retry, and eventually an incident shows up in Operate for
an operator to look at.

Every worker carries `businessKey = incidentId`, so the `IdempotencyGuard` short-circuits a
redelivered alert or a replayed job. The actions themselves are written to be naturally idempotent
too; blocking an IP twice should be a no-op.

Two escalation timers, both non-interrupting so the task stays open: `=slaDuration` on CISO Review
(derived from severity: 4h for P1 through 72h for P4) and `=regulatoryDeadline` on the regulatory
task (72 hours unless the caller overrides it).

Compensation isn't modelled. Nothing here is a monetary or otherwise irreversible saga step, so
there's nothing to unwind; `WorkResult.compensated()` is there if that changes.

## 6. Backend

Hexagonal layering with ArchUnit enforcing it: web talks to application, application talks to
infrastructure, and only `infrastructure.camunda` ever imports the Camunda client. The `Incident`
aggregate is an `AuditableEntity`, so illegal status transitions throw rather than silently
corrupt state. Postgres via Flyway.

The task API (`/incidents/{id}/tasks…`) exists so completions can be scripted and, more usefully,
so the submitted form data is persisted to `incident_task_outcomes`. Camunda's history expires,
and for an incident record that's the part you actually want to keep.

## 7. What's been verified

A P1 driven end to end to CLOSED: parallel forensic analysis and containment verification, then
CISO review, integrity verification, regulatory filing and closure, with all six outcomes in
Postgres.

All five boundary events, driven on one incident (`forceIsolationFailure` + `forceAiFailure` +
`slaDuration:PT20S` + `regulatoryDeadline:PT20S`): AI triage fell back to the worker, isolation
failure escalated to the commander, the SLA timer fired 20s into CISO Review without cancelling it,
the regulatory timer fired 20s into the Legal task without cancelling it, the AI report fell back,
and the incident still reached CLOSED. A control run without `forceAiFailure` produced no fallback
row, which confirms the AI steps genuinely succeed when the connector is healthy and that the
failure injection is real rather than an artefact of a missing secret.

Every automated event lands in `incident_task_outcomes` as a `system:process` row next to the human
completions, so the audit trail is one ordered list of what happened and who did it. P4 auto-close
and the ad-hoc subsets (including Deploy Patch, which the default never activates) verified too.

**The form rewrite** was verified statically, ahead of a cluster run. All seven forms validate
against the published `@bpmn-io/form-json-schema`, so the component types, per-type required
properties and enum values are right rather than plausible. Every briefing panel was rendered through
the real `feelers` engine against three variable sets — a fresh instance where automated isolation
succeeded, the error path where the commander contained by hand, and a fully populated late-stage
instance — asserting no unresolved `{{…}}` and no bare `null` reaches the screen. That pass is what
caught `get or else()` being absent from feelin. The two forensic expression components were
evaluated across seven input combinations to confirm that omitting the new fields leaves
`dataExposed` and `recordCount` untouched, and the report prompt was evaluated after being read back
out of the BPMN attribute, including the worst case where the regulatory branch was skipped and every
optional field is absent. Still outstanding: deploying to the cluster and opening each form in
Tasklist, which is the only way to confirm the panels lay out as intended.

## 8. Assumptions

The workers stand in for real integrations: SIEM, EDR, firewall, IAM, email. Without
`OPENAI_API_TOKEN` configured in Console the AI steps fail over to their fallbacks, which is fine
for a walkthrough but means the triage and report text is canned.
