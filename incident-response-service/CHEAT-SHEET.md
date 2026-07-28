# UC4 One-Page Cheat-Sheet (glance during the walkthrough)

Colour = element type · 👤 = human task (Tasklist) with its candidate group · dashed = error / timer escalation.

```mermaid
flowchart TD
    start(["SIEM alert"]):::ev
    start --> triageAI["AI Threat Triage — AI Agent"]:::ai
    triageAI --> recTriage["Record Triage — worker triage-threat · →TRIAGED"]:::wk
    triageAI -. "AI_STEP_FAILED" .-> recTriage
    recTriage --> classify["Classify Incident — DMN incident-classification · →severity"]:::dmn
    classify --> recClass["Record Classification — worker record-classification · →CLASSIFIED · sets slaDuration"]:::wk
    recClass --> p4{"P4 / false positive?"}:::gw
    p4 -- "P4" --> autoclose["Auto Close — worker auto-close · →AUTO_CLOSED"]:::wk
    autoclose --> endAuto(["Auto-closed"]):::ev
    p4 -- "P1–P3" --> split{{"Parallel split (response streams)"}}:::gw

    subgraph CONT["Containment (embedded sub-process)"]
      direction TB
      C_iso["Isolate Systems — worker isolate-systems"]:::wk
      C_handle["Handle Isolation Failure — 👤 incident-commander"]:::ut
      C_verify["Containment Verification — 👤 soc-analyst"]:::ut
      C_iso -. "ISOLATION_FAILED" .-> C_handle
      C_iso --> C_verify
      C_handle --> C_verify
    end

    subgraph FOR["Forensics (embedded sub-process)"]
      direction TB
      F_ev["Collect Evidence — worker collect-evidence"]:::wk
      F_an["Forensic Analysis — 👤 forensics-lead"]:::ut
      F_ev --> F_an
    end

    subgraph ADHOC["Response Actions (ad-hoc — commander selects at runtime, repeatable)"]
      direction TB
      A1["Block IP — worker block-ip"]:::wk
      A2["Revoke Credentials — worker revoke-credentials"]:::wk
      A3["Deploy Patch — worker deploy-patch"]:::wk
    end

    notify["Notify Stakeholders — worker notify-stakeholders"]:::wk

    split --> C_iso
    split --> F_ev
    split --> notify
    split --> ADHOC

    join{{"Parallel join (wait for all 4)"}}:::gw
    C_verify --> join
    F_an --> join
    notify --> join
    ADHOC --> join

    join --> ciso["CISO Review — 👤 ciso"]:::ut
    ciso -. "SLA timer =slaDuration" .-> escSla["Escalate SLA — worker escalate"]:::wk
    escSla --> endSla(["SLA escalated"]):::ev

    subgraph REC["Recovery (embedded sub-process)"]
      direction TB
      restore["Restore Services — worker restore-services · →RECOVERING"]:::wk
      integ["Integrity Verification — 👤 soc-analyst"]:::ut
      restore --> integ
    end
    ciso --> restore

    integ --> reg["Regulatory Required? — DMN regulatory-notification"]:::dmn
    reg --> reggw{"Regulatory required?"}:::gw
    reggw -- "yes" --> fileReg["File Regulatory Notification — 👤 legal-compliance"]:::ut
    fileReg -. "72h timer PT72H" .-> escCiso["Escalate to CISO — worker escalate"]:::wk
    escCiso --> endReg(["Deadline escalated"]):::ev
    reggw -- "no" --> merge{"Merge"}:::gw
    fileReg --> merge
    merge --> reportAI["AI Post-Incident Report — AI Agent"]:::ai
    reportAI --> closure["Incident Closure — 👤 incident-commander"]:::ut
    reportAI -. "AI_STEP_FAILED" .-> genReport["Generate Report — worker generate-report"]:::wk
    genReport --> closure
    closure --> close["Close Incident — worker close-incident · →CLOSED"]:::wk
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
| 🟦 Blue | Spring Boot **job worker** (automated service task) |
| 🟧 Orange | **DMN** business rule task (decision) |
| 🟪 Purple | **AI Agent** connector step |
| 🟩 Green | **Human task** in Tasklist (👤 + candidate group) |
| 🟨 Yellow diamond/hex | **Gateway** (exclusive / parallel) |
| ⬜ Grey | Start / End event |
| ┄ Dashed | **Error boundary** or **non-interrupting timer** escalation |

## The 30-second narration
**SIEM alert → AI triage → DMN classify (P1–P4; P4 auto-closes) → parallel [Containment · Forensics ·
Notify · ad-hoc actions] → CISO review (SLA timer) → Recovery → DMN regulatory (72h timer) → AI report
→ commander closes.** Automated steps are workers; decisions are DMN; reasoning is AI; people act in
Tasklist; deadlines are timers.

## Numbers to say
**13** workers · **2** DMN (both FIRST) · **2** AI Agent steps · **1** ad-hoc sub-process · **3**
embedded sub-processes · **7** Tasklist forms · **5** personas · **2** BPMN errors · **2** escalation timers.

## Status machine (persisted in `incidents.status`)
`RAISED → TRIAGED → CLASSIFIED → RECOVERING → CLOSED`  ·  early exit `→ AUTO_CLOSED` (P4).
SLA by severity: **P1 PT4H · P2 PT8H · P3 PT24H**. Regulatory deadline: **PT72H**.

## Personas → candidate groups
SOC Analyst `soc-analyst` · Incident Commander `incident-commander` · Forensics Lead `forensics-lead`
· CISO `ciso` · Legal/Compliance `legal-compliance`.

## Three exception paths to show
- **Isolation fails** (`forceIsolationFailure:true`) → BPMN error → **Handle Isolation Failure** (commander).
- **False positive** (`attackConfirmed:false`) → DMN **P4** → **auto-close**, no human tasks.
- **72h deadline** on the regulatory task → non-interrupting timer → **Escalate to CISO** (task stays open).

## One-liner answers (if asked "why")
Embedded sub-process = no reuse + shared vars + one deployable · Ad-hoc = runtime, repeatable selection
· DMN FIRST = single deterministic output over an ordered precedence list · AI + fallback = never stalls
· BPMN error vs incident = expected-deviation-to-human vs technical-retry-to-Operate · Non-interrupting
timer = escalate in parallel without cancelling the task · Idempotent = `businessKey` + guard.
