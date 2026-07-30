# Cheat sheet

For glancing at during the walkthrough. Colour is the element type, 👤 marks a Tasklist task with
its candidate group, dashed lines are error or timer escalations.

```mermaid
flowchart TD
    start(["SIEM alert"]):::ev
    start --> triageAI["AI Threat Triage - AI Agent"]:::ai
    triageAI --> recTriage["Record Triage - worker triage-threat · →TRIAGED"]:::wk
    triageAI -. "AI_STEP_FAILED" .-> recTriage
    recTriage --> classify["Classify Incident - DMN incident-classification · →severity"]:::dmn
    classify --> recClass["Record Classification - worker record-classification · →CLASSIFIED · sets slaDuration"]:::wk
    recClass --> p4{"P4 / false positive?"}:::gw
    p4 -- "P4" --> autoclose["Auto Close - worker auto-close · →AUTO_CLOSED"]:::wk
    autoclose --> endAuto(["Auto-closed"]):::ev
    p4 -- "P1-P3" --> split{{"Parallel split (response streams)"}}:::gw

    subgraph CONT["Containment (embedded sub-process)"]
      direction TB
      C_iso["Isolate Systems - worker isolate-systems"]:::wk
      C_handle["Handle Isolation Failure - 👤 incident-commander"]:::ut
      C_verify["Containment Verification - 👤 soc-analyst"]:::ut
      C_iso -. "ISOLATION_FAILED" .-> C_handle
      C_iso --> C_verify
      C_handle --> C_verify
    end

    subgraph FOR["Forensics (embedded sub-process)"]
      direction TB
      F_ev["Collect Evidence - worker collect-evidence"]:::wk
      F_an["Forensic Analysis - 👤 forensics-lead"]:::ut
      F_ev --> F_an
    end

    subgraph ADHOC["Response Actions (ad-hoc - commander selects at runtime, repeatable)"]
      direction TB
      A1["Block IP - worker block-ip"]:::wk
      A2["Revoke Credentials - worker revoke-credentials"]:::wk
      A3["Deploy Patch - worker deploy-patch"]:::wk
    end

    notify["Notify Stakeholders - worker notify-stakeholders"]:::wk

    split --> C_iso
    split --> F_ev
    split --> notify
    split --> ADHOC

    join{{"Parallel join (wait for all 4)"}}:::gw
    C_verify --> join
    F_an --> join
    notify --> join
    ADHOC --> join

    join --> ciso["CISO Review - 👤 ciso"]:::ut
    ciso -. "SLA timer =slaDuration" .-> escSla["Escalate SLA - worker escalate"]:::wk
    escSla --> endSla(["SLA escalated"]):::ev

    subgraph REC["Recovery (embedded sub-process)"]
      direction TB
      restore["Restore Services - worker restore-services · →RECOVERING"]:::wk
      integ["Integrity Verification - 👤 soc-analyst"]:::ut
      restore --> integ
    end
    ciso --> restore

    integ --> reg["Regulatory Required? - DMN regulatory-notification"]:::dmn
    reg --> reggw{"Regulatory required?"}:::gw
    reggw -- "yes" --> fileReg["File Regulatory Notification - 👤 legal-compliance"]:::ut
    fileReg -. "72h timer =regulatoryDeadline" .-> escCiso["Escalate to CISO - worker escalate"]:::wk
    escCiso --> endReg(["Deadline escalated"]):::ev
    reggw -- "no" --> merge{"Merge"}:::gw
    fileReg --> merge
    merge --> reportAI["AI Post-Incident Report - AI Agent"]:::ai
    reportAI --> closure["Incident Closure - 👤 incident-commander"]:::ut
    reportAI -. "AI_STEP_FAILED" .-> genReport["Generate Report - worker generate-report"]:::wk
    genReport --> closure
    closure --> close["Close Incident - worker close-incident · →CLOSED"]:::wk
    close --> endClosed(["Closed"]):::ev

    classDef wk fill:#dbeafe,stroke:#3b82f6,color:#0b2447;
    classDef dmn fill:#ffedd5,stroke:#f97316,color:#7c2d12;
    classDef ai fill:#ede9fe,stroke:#8b5cf6,color:#4c1d95;
    classDef ut fill:#dcfce7,stroke:#22c55e,color:#14532d;
    classDef gw fill:#fef9c3,stroke:#eab308,color:#713f12;
    classDef ev fill:#e5e7eb,stroke:#6b7280,color:#111827;
```

## Legend

| Colour | Meaning |
|---|---|
| 🟦 Blue | Spring Boot job worker |
| 🟧 Orange | DMN business rule task |
| 🟪 Purple | AI Agent connector step |
| 🟩 Green | Human task in Tasklist |
| 🟨 Yellow | Gateway |
| ⬜ Grey | Start / end event |
| ┄ Dashed | Error boundary or non-interrupting timer |

## The 30-second version

SIEM alert, AI triage, DMN classifies P1-P4 (P4 auto-closes). Everything else fans out into
containment, forensics, notification and ad-hoc actions, joins at CISO review, recovers, checks
whether Legal has to file, gets an AI write-up, and the commander closes it.

Workers do the automation, DMN makes the decisions, AI does the reasoning, people act in Tasklist,
timers enforce the deadlines.

## Numbers

13 workers, 2 DMN tables (both FIRST), 2 AI Agent steps, 1 ad-hoc sub-process, 3 embedded
sub-processes, 7 forms, 5 personas, 2 BPMN errors, 2 escalation timers.

## Status machine

`RAISED → TRIAGED → CLASSIFIED → RECOVERING → CLOSED`, or straight to `AUTO_CLOSED` on P4.
SLA by severity: P1 PT4H, P2 PT8H, P3 PT24H. Regulatory deadline defaults to PT72H. Both are
process variables (`slaDuration`, `regulatoryDeadline`), so a demo can override them to seconds.

## Personas

`soc-analyst`, `incident-commander`, `forensics-lead`, `ciso`, `legal-compliance`.

## Forms

Each of the 7 forms opens with a read-only briefing panel of what the process knows at that point,
then asks for that role's findings. The chain that matters: forensics records attack vector, MITRE
technique, root cause, scope, IOCs, data categories and exfiltration status → the CISO reviews all of
it on one screen before rating and accepting residual risk → the analyst verifying integrity sees the
recovery conditions the CISO imposed → the commander closes against the full AI report.

Two forensic fields feed the automation back: `exfiltrationConfirmed` + `dataCategories` recompute
`dataExposed`, and `confirmedRecordCount` overrides `recordCount`, so the regulatory DMN decides on
confirmed findings rather than the SIEM's estimate. Leave them empty and the old values stand.

Sign-off checkboxes (`containmentVerified`, `recoveryAuthorized`, `integrityVerified`,
`notificationFiled`, `lessonsLearnedCaptured`) are `required`: the model has no rejection branch, so
declining means leaving the task open rather than submitting a "no" nothing would act on.

## Exception paths worth showing

All five are reachable from the raise body, and every automated one leaves a `system:process` row in
`GET /incidents/{id}/tasks/outcomes`.

- `forceIsolationFailure:true` - isolation throws a BPMN error, commander gets Handle Isolation Failure.
- `forceAiFailure:true` - both AI Agent tasks get a model id OpenAI rejects, so both error
  boundaries fire and both fall back to workers. The incident still reaches CLOSED.
- `slaDuration:"PT20S"` - trips the CISO review SLA timer without waiting 4 hours.
- `regulatoryDeadline:"PT20S"` - trips the 72h timer on the regulatory task. Non-interrupting, so
  Legal keeps the task; the escalation just tells the CISO the clock ran out.
- `attackConfirmed:false` - DMN says P4, auto-closes, no human tasks at all.
- `responseActions:[...]` - which ad-hoc actions run; pass all three to include Deploy Patch.

## If they ask why

Embedded sub-process because nothing reuses them and they share the parent's variables. Ad-hoc
because the commander picks actions as findings come in, not up front. FIRST because both tables
are an ordered precedence list and we want one answer. AI with a fallback so an outage never stalls
an incident. BPMN error for expected deviations that need a human; incidents for technical failures
that need an operator. Non-interrupting timers so escalating doesn't cancel the task someone is
working on. Idempotency off `businessKey` so a redelivered alert doesn't double-apply.
