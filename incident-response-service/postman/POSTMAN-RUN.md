# Postman run-book

Ten scenarios, each one: which request to hit, what to send, **why that input**, and what comes
back. Every output below was observed against a running service, not written from the model.

For what the collection contains, see [README.md](README.md). For the modelling rationale, see
[../PROJECT-UNDERSTANDING.md](../PROJECT-UNDERSTANDING.md).

## Before you start

1. PostgreSQL 17 running, database `incident_response`.
2. `mvn spring-boot:run "-Dspring-boot.run.profiles=local"` — wait for `Started
   IncidentResponseApplication` and `Deployed Processes: <incident-response:N>`.
3. Import `incident-response-service.postman_collection.json`.
4. Set `baseUrl` under the collection's Variables tab if you aren't on `http://localhost:8080`.
5. Open the Postman Console (**View → Show Postman Console**). The test scripts log incident ids,
   task names and severities there — most of the useful output is in the console, not the body.

`{{incidentId}}`, `{{taskKey}}` and `{{taskKey2}}` are set automatically by the test scripts. You
never type an id by hand.

## Pick a scenario

| # | Scenario | Shows | Time |
| --- | --- | --- | --- |
| 1 | [Happy path — P1 breach, start to finish](#1) | the whole orchestration | ~5 min |
| 2 | [Negative — bad input rejected at the boundary](#2) | validation and error contract | ~2 min |
| 3 | [Happy path *through* the error paths](#3) | all 5 boundary events, still closes | ~8 min |
| 4 | [P4 false positive — auto-close](#4) | alert-noise reduction | ~1 min |
| 5 | [P2 — regulatory branch skipped](#5) | the two DMNs are independent | ~4 min |
| 6 | [P1 under the record threshold](#6) | severity ≠ "must notify" | ~4 min |
| 7 | [Isolation failure on its own](#7) | error boundary → human decision | ~4 min |
| 8 | [AI failure on its own](#8) | LLM outage degrades, doesn't stop | ~1 min |
| 9 | [SLA and 72h timers on their own](#9) | non-interrupting escalation | ~5 min |
| 10 | [Ad-hoc response actions](#10) | commander picks, process doesn't dictate | ~4 min |

**Doing a 30-minute demo?** Run 4 → 1 → 3. Noise reduction, then the full flow, then resilience.

## Reading the output

Successful responses are wrapped: `{"data": ..., "meta": {...}}` — the test scripts read `data`.
Errors are RFC 7807 problem details: `status`, `title`, `detail`, plus `fieldErrors` for validation
or `errorCode` for business errors.

Two things to know before you start clicking:

- **Allow 15–30 seconds after raising** before tasks appear. There's a real OpenAI call in there,
  and Camunda's search index lags the engine by a moment. An empty task list usually means "not
  yet", not "broken" — re-run **List active tasks**.
- **Task order is not stable.** The parallel phase returns Containment Verification and Forensic
  Analysis in either order. Read the names the console logs; don't assume `{{taskKey}}` is a
  particular one.

---

<a id="1"></a>
## Scenario 1 — Happy path: a P1 breach, start to finish

**The story:** the SIEM reports ransomware on a production database with 25,000 customer records
exposed. Nothing fails. This is the reference run.

### Step 1 — Raise it

**Folder 1 → "P1 - default triage signals (happy path)"**

```json
{ "title": "Ransomware + data exfiltration on prod-db", "source": "SIEM" }
```

**Why only two fields:** everything else defaults to the worst case — `attackConfirmed:true`,
`assetCriticality:HIGH`, `dataExposed:true`, `recordCount:25000`. This is the "SIEM sends the
minimum" case, and it deliberately lands on the path that exercises the most elements.

**Output:** `201`, `status: RAISED`. The test script saves `{{incidentId}}`. Don't skip this — every
later request resolves that variable.

### Step 2 — Wait ~20s, then read the state

**Folder 2 → "Get incident"**

**Why wait:** between steps 1 and 2 the process runs unattended — AI triage calls OpenAI, the
classification DMN runs, a worker persists the severity and derives the SLA, and the parallel
gateway opens four branches.

**Output:** `status: CLASSIFIED`, `severity: P1`.

**How it connects:** `P1` came out of the DMN from the three input signals. That same `P1` sets
`slaDuration = PT4H`, which the CISO review timer reads later.

### Step 3 — List the open tasks

**Folder 3 → "List active tasks"**

**Output:** **two** tasks — `Containment Verification` (soc-analyst) and `Forensic Analysis`
(forensics-lead), saved to `{{taskKey}}` and `{{taskKey2}}`.

**Why two:** containment and forensics are parallel branches. In a real SOC they run at the same
time and they conflict — containment wants the machine powered off, forensics needs it on to
capture memory. The model reflects that instead of pretending it's sequential.

### Step 4 — Complete both

**Folder 3 → "Complete task by key ({{taskKey}})"**, then **"Complete 2nd task by key
({{taskKey2}})"**

**Why by key:** with two open, the convenience endpoint can't know which you mean — it returns
`422 AMBIGUOUS_TASK` on purpose (scenario 2, step 3).

**Output:** `200` each, with the task name echoed back so you can see which was which.

### Step 5 — CISO Review

**Folder 3 → "List active tasks"** → one task, `CISO Review`.
Then **Folder 4 → "CISO Review (ciso)"**

```json
{ "completedBy": "ciso",
  "variables": { "residualRiskAccepted": true, "recoveryAuthorized": true, "cisoNotes": "..." } }
```

**Why folder 4 rather than folder 3 here:** folder 3's shared body sends all 16 form fields at once
and lets Camunda ignore the irrelevant ones — convenient, but the audit row then contains fields the
CISO never saw. Folder 4 sends only this task's real fields.

**Why the gate exists:** nothing reaches recovery or a regulator until an accountable executive
signs off.

### Step 6 — Integrity Verification

**Folder 3 → "List active tasks"** → `Integrity Verification`.
Then **Folder 3 → "Complete active task"** — one task open, so the convenience endpoint works.

**How it connects:** completing CISO Review entered the Recovery sub-process. The `restore-services`
worker already ran; this human step confirms the restored systems are clean before they go back on
the network.

### Step 7 — File Regulatory Notification

**Folder 3 → "List active tasks"** → `File Regulatory Notification`.
Then **Folder 4 → "File Regulatory Notification (legal-compliance)"**

**Why this task appeared:** a **second, independent** DMN ran after recovery — `dataExposed:true`
AND `recordCount:25000 ≥ 500` → `regulatoryRequired:true` → the gateway routed to Legal. Scenarios 5
and 6 are the same flow with that decision going the other way.

### Step 8 — Closure

**Folder 3 → "List active tasks"** → `Incident Closure`.
Then **Folder 4 → "Incident Closure (incident-commander)"**.

### Step 9 — Prove it

**Folder 2 → "Get incident"** → `status: CLOSED`
**Folder 3 → "Task outcomes"** → six rows, oldest first, each with who completed it and what they
submitted.

**Verified output — six human tasks, in this order:**

```
Containment Verification, Forensic Analysis, CISO Review,
Integrity Verification, File Regulatory Notification, Incident Closure
→ severity P1, status CLOSED
```

**What you proved:** DMN-driven classification, parallel human work, an executive gate, a second
independent regulatory decision, and an audit trail that outlives Camunda's history retention.

---

<a id="2"></a>
## Scenario 2 — Negative: bad input is rejected at the boundary

**The story:** mistakes fail fast and legibly instead of returning `201` and breaking a process
instance nobody is watching. All of folder 5 — 14 requests, none of which start a process. Run them
in any order.

### Step 1 — Missing required field

**Folder 5 → "Raise without title -> 400"** — body is `{"source":"SIEM"}`

**Output:** `400`, `fieldErrors: [{field: "title", ...}]`. `title` is `@NotBlank`.

### Step 2 — Unknown incident

**Folder 5 → "Get unknown incident -> 422 INCIDENT_NOT_FOUND"**

**Why 422 and not 404:** the framework maps `BusinessException` to 422 with a stable `errorCode` a
client can branch on. Same shape from the incident endpoint and the task endpoints.

### Step 3 — Ambiguous task

**Folder 5 → "Complete active task with none / two active -> 422"**

**When to run it:** during scenario 1 step 3, while both parallel tasks are open →
`AMBIGUOUS_TASK`. Right after a P4 raise → `NO_ACTIVE_TASK`. Same request, two different errors
depending on process state.

### Step 4 — A bad timer duration (the one that matters)

**Folder 5 → "Invalid slaDuration -> 400"**

```json
{ "title": "bad SLA duration", "source": "SIEM", "slaDuration": "20 seconds" }
```

**Why this is the interesting one:** `slaDuration` becomes a **BPMN timer expression**. Without this
check the request returns `201` and the failure surfaces minutes later as an incident in Operate, on
a token nobody is watching. Rejecting it here turns a stuck process into a typo.

**Output:** `400`, `"must be an ISO-8601 duration, e.g. PT20S, PT4H, P3D"`.

### Step 5 — Unknown ad-hoc element id

**Folder 5 → "Unknown ad-hoc element id -> 400"**

```json
{ "responseActions": ["Task_BlockIp", "Task_NukeEverything"] }
```

**Output:** `400`, and the field is **`responseActions[1]`** — it names the index so you know which
entry is wrong.

### Step 6 — Wrong enum

**Folder 5 → "Invalid assetCriticality -> 400"** — `"assetCriticality": "EXTREME"`

**Why reject rather than tolerate:** the DMN only has rules for LOW/MEDIUM/HIGH/CRITICAL. `EXTREME`
would fall through to the catch-all rule and come back **P3** — which reads like the decision table
is broken rather than the request.

### Step 7 — Unparseable input

**Folder 5 → "Malformed JSON body"**, **"Field of the wrong type"**, **"Path id that isn't a
UUID"**, **"Complete an unknown userTaskKey"**

**Output:** `400` with a `rejectedValue` naming the offending value; the last is `422
USER_TASK_NOT_FOUND`.

**Why they're in the collection:** these four used to return **500 "An unexpected error occurred."**
The framework's handler covers bean validation and its own exception types, but Jackson parse
failures and path-variable conversion failures fell through to its `Exception` catch-all — so a
client's typo was reported as the server's fault.

**Verified: all 14 requests in folder 5 pass, and all 14 valid raise bodies in folders 1 and 6 still
return `201` — the validation rejects nothing it shouldn't.**

---

<a id="3"></a>
## Scenario 3 — Happy path *through* the error paths

**The story:** the worst night of the year. The LLM provider is down, automated isolation fails, the
CISO is asleep past the SLA, and Legal misses the notification deadline. **The incident still
closes.** This is the resilience argument, and it starts with one request.

### Step 1 — Raise it

**Folder 6 → "Every boundary event in one run (full sweep)"**

```json
{ "title": "Worst-case incident - everything that can go wrong does",
  "source": "SIEM",
  "forceIsolationFailure": true,
  "forceAiFailure": true,
  "slaDuration": "PT20S",
  "regulatoryDeadline": "PT20S",
  "responseActions": ["Task_BlockIp", "Task_RevokeCredentials", "Task_DeployPatch"],
  "attackConfirmed": true, "assetCriticality": "CRITICAL",
  "dataExposed": true, "recordCount": 25000 }
```

**Why each input:**

| Input | Trips | Why it has to be an input |
| --- | --- | --- |
| `forceAiFailure` | error boundaries on **both** AI Agent tasks | points them at a model id OpenAI rejects, so the connector genuinely 4xx's — not a stubbed flag |
| `forceIsolationFailure` | error boundary on Isolate Systems | the worker throws a real BPMN error |
| `slaDuration: PT20S` | CISO Review SLA timer | a P1 is normally `PT4H`; you can't wait 4 hours in a demo |
| `regulatoryDeadline: PT20S` | 72h deadline timer | same problem, 72 hours |
| all three `responseActions` | ad-hoc sub-process | the default activates only two, so Deploy Patch never runs otherwise |
| `CRITICAL` + `25000` | both DMNs | forces P1 **and** makes the Legal task appear, so both timers are reachable |

### Step 2 — Wait ~20s, then list tasks

**Folder 3 → "List active tasks"**

**Verified output — and this is the tell:**

```
Forensic Analysis          [6755399442138619]
Handle Isolation Failure   [6755399442138624]   ← NOT Containment Verification
```

**Why:** the `isolate-systems` worker threw `ISOLATION_FAILED`. The error boundary **inside the
Containment sub-process** caught it and routed to the commander. Isolation failing is a decision for
a human, not something to retry in a loop.

Meanwhile AI triage already failed and fell back silently — confirm with **Folder 6 → "Verify
boundary & timer events"**, which shows `AI threat triage fallback`.

### Step 3 — Complete both, then the extra task

Complete both by key. Re-list → **`Containment Verification`** now appears.

**Why an extra step:** the error path rejoins the normal flow. The commander handles the failure,
*then* the analyst still verifies containment. The deviation is absorbed, not skipped.

### Step 4 — Park on CISO Review for ~25 seconds

Complete Containment Verification, re-list → `CISO Review`. **Now do nothing.**

Then **Folder 6 → "Verify boundary & timer events"** → new row, `SLA breach escalation (CISO
review)`. Re-list tasks → **`CISO Review` is still open.**

**Why that matters:** the timer is **non-interrupting**. It escalates without cancelling the work.
An interrupting timer would delete the CISO's task, which is exactly wrong — you want to page
someone *and* keep the task.

Now complete CISO Review normally.

### Step 5 — Integrity Verification, then park on Legal for ~25s

Complete Integrity Verification, re-list → `File Regulatory Notification`. **Wait again.**

**Verify boundary & timer events** → `Regulatory deadline escalation (72h)`. Task still open.

**Why this one matters commercially:** under GDPR Art. 33 that clock is real. Missing it is a fine,
not an inconvenience.

### Step 6 — Finish

Complete the Legal task → the AI report step fails and falls back → complete `Incident Closure`.

### Step 7 — The payoff

**Folder 2 → "Get incident"** → `CLOSED`, severity `P1`.
**Folder 6 → "Verify boundary & timer events"** — verified output:

```
AI threat triage fallback              system:process   ← AI boundary 1
Forensic Analysis                      api.tester
Handle Isolation Failure               api.tester       ← isolation boundary
Containment Verification               api.tester
SLA breach escalation (CISO review)    system:process   ← timer boundary 1
CISO Review                            ciso
Integrity Verification                 soc.analyst
Regulatory deadline escalation (72h)   system:process   ← timer boundary 2
File Regulatory Notification           legal.compliance
AI post-incident report fallback       system:process   ← AI boundary 2
Incident Closure                       incident.commander
```

**What you proved:** all five boundary events fired and the incident still reached CLOSED. Human and
automated events sit in one ordered list — which is what you hand a regulator when they ask what
happened and who did it.

---

<a id="4"></a>
## Scenario 4 — P4 false positive: auto-close

**The story:** most of what a SOC sees at 2am is noise. This is the request that shows the process
throwing it away without waking anyone.

### Steps

1. **Folder 1 → "P4 - false positive (auto-close)"**

   ```json
   { "title": "Benign alert - false positive", "source": "SIEM",
     "attackConfirmed": false, "assetCriticality": "LOW",
     "dataExposed": false, "recordCount": 0 }
   ```

   **Why `attackConfirmed:false`:** it hits the **first** rule in the table, and the hit policy is
   FIRST, so nothing below it is even considered. The other two signals are ignored.

2. Wait ~15s. **Folder 2 → "Get incident"**.

**Verified output:** `severity: P4`, `status: AUTO_CLOSED`, **zero human tasks**.

3. Optional: **Folder 3 → "List active tasks"** → empty, then **Folder 5 → "Complete active task
   with none / two active"** → `422 NO_ACTIVE_TASK`.

**What you proved:** the difference between a SOC drowning in 400 alerts a night and one handling
the six that matter. Say this out loud in a demo — it's the clearest business number in the model.

---

<a id="5"></a>
## Scenario 5 — P2: the regulatory branch is skipped

**The story:** a confirmed attack on a high-value asset, but nothing was exfiltrated. Serious, but
there's nobody to notify.

### Steps

1. **Folder 1 → "P2 - HIGH asset, no data exposed"**

   ```json
   { "title": "Contained breach, no data exfiltration", "source": "SIEM",
     "attackConfirmed": true, "assetCriticality": "HIGH",
     "dataExposed": false, "recordCount": 0 }
   ```

   **Why `dataExposed:false`:** it's the only difference from the scenario-1 body. It drops severity
   from P1 to P2 *and* makes the regulatory DMN return false — one field, two independent
   consequences.

2. Drive it exactly like scenario 1: parallel pair → CISO Review → Integrity Verification →
   Incident Closure.

**Verified output — five human tasks, no Legal step:**

```
Forensic Analysis, Containment Verification, CISO Review,
Integrity Verification, Incident Closure
→ severity P2, status CLOSED
```

3. Note the SLA in Operate: `slaDuration` is `PT8H` here, not `PT4H`. A P2 gets the CISO twice as
   long.

**What you proved:** the regulatory gateway genuinely branches. Watch for `File Regulatory
Notification` never appearing — that absence is the result.

---

<a id="6"></a>
## Scenario 6 — P1 under the record threshold

**The story:** the pair worth showing back to back with scenario 1. Data *was* exposed, the incident
*is* a P1 — and Legal still doesn't have to file.

### Steps

1. **Folder 1 → "P1 - data exposed but under the 500-record threshold"**

   ```json
   { "title": "Small-scale credential leak", "source": "Threat Intel",
     "attackConfirmed": true, "assetCriticality": "HIGH",
     "dataExposed": true, "recordCount": 120 }
   ```

   **Why `recordCount:120`:** the classification DMN never looks at `recordCount` — it's still P1 on
   confirmed + HIGH + exposed. The regulatory DMN needs `≥ 500`, so 120 falls through to its default
   `false`.

2. Drive to CLOSED as before.

**Verified output — five human tasks, P1, no Legal step:**

```
Containment Verification, Forensic Analysis, CISO Review,
Integrity Verification, Incident Closure
→ severity P1, status CLOSED
```

**What you proved:** severity and "does Legal have to file" are **two separate decisions** with
different inputs. Run this immediately after scenario 1 — same severity, same effort, different
regulatory outcome. If anyone asks why there are two DMN tables instead of one, this is the answer.

---

<a id="7"></a>
## Scenario 7 — Isolation failure on its own

**The story:** the endpoint agent is dead, or the host is already off the network. Automated
containment can't do its job.

### Steps

1. **Folder 1 → "Isolation failure (BPMN error escalation)"**

   ```json
   { "title": "Isolation-failure demo", "source": "SIEM",
     "forceIsolationFailure": true, "attackConfirmed": true,
     "assetCriticality": "CRITICAL", "dataExposed": true, "recordCount": 25000 }
   ```

   **Why isolated from the other flags:** scenario 3 fires everything at once, which is dramatic but
   busy. Use this one when you want to explain *just* the error boundary.

2. Wait ~20s. **Folder 3 → "List active tasks"**.

**Verified output:** `Handle Isolation Failure` (incident-commander) and `Forensic Analysis` — **not**
Containment Verification.

3. **Folder 4 → "Handle Isolation Failure (incident-commander)"**

   ```json
   { "completedBy": "incident.commander",
     "variables": { "manuallyContained": true,
       "isolationFailureAction": "Automated isolation failed; hosts pulled from the network manually" } }
   ```

4. Re-list → `Containment Verification` appears. Carry on to CLOSED.

**What you proved:** the difference between a **BPMN error** and an **incident**. A failed isolation
is an expected deviation with a human owner, so it's modelled as an error boundary with a task. A
database being down is a technical failure, so it becomes a Camunda incident and retries. Those are
not the same thing and the model treats them differently.

---

<a id="8"></a>
## Scenario 8 — AI failure on its own

**The story:** OpenAI is down, rate-limiting you, or the API key expired. Sixty seconds, no human
tasks — the cheapest possible proof.

### Steps

1. **Folder 6 → "AI triage failure -> fallback worker (fast, P4)"**

   ```json
   { "title": "AI triage failure demo", "source": "SIEM",
     "forceAiFailure": true, "attackConfirmed": false,
     "assetCriticality": "LOW", "dataExposed": false, "recordCount": 0 }
   ```

   **Why P4 as well:** `attackConfirmed:false` makes it auto-close immediately after the AI step, so
   you see the boundary fire without driving six human tasks.

2. Wait ~20s. **Folder 6 → "Verify boundary & timer events"**.

**Verified output:**

```json
{ "userTaskKey": 0, "elementId": "Task_Triage",
  "taskName": "AI threat triage fallback", "completedBy": "system:process",
  "outcome": "{\"reason\":\"AI_STEP_FAILED\",\"fallback\":\"triage-threat worker\"}" }
```

And **Folder 2 → "Get incident"** → `P4 / AUTO_CLOSED`. It classified correctly *without* the AI.

### The control run — do this one too

Raise the same body **without** `forceAiFailure` (**Folder 1 → "P4 - false positive"**), then check
outcomes.

**Verified output: empty.** No fallback row, because the AI genuinely succeeded.

**Why bother:** it's the difference between "the AI path is broken so of course the fallback ran"
and "the AI works, and we deliberately broke it." Without the control, the failure test proves
nothing. Run the pair.

3. For the **second** AI boundary, use **Folder 6 → "AI failure on both steps -> drive to CLOSED
   (P2)"** and drive it through — the post-incident report step is near the end, so you have to
   reach it. Outcomes then show both `AI threat triage fallback` and `AI post-incident report
   fallback`.

**What you proved:** an LLM outage degrades the process — canned text instead of generated text —
rather than stopping it. The AI is an enrichment, not a dependency.

---

<a id="9"></a>
## Scenario 9 — The two escalation timers, one at a time

**The story:** deadlines that enforce themselves. This is the **core capability** of the use case,
so it's worth showing on its own rather than buried in scenario 3.

### 9a — CISO review SLA

1. **Folder 6 → "CISO SLA breach escalation (timer, 20s)"**

   ```json
   { "title": "SLA breach demo - unattended P1", "source": "SIEM",
     "slaDuration": "PT20S", "attackConfirmed": true,
     "assetCriticality": "CRITICAL", "dataExposed": false, "recordCount": 0 }
   ```

   **Why `dataExposed:false`:** it keeps the Legal task out of the way so you're only demonstrating
   one timer.

   **Why `PT20S`:** a P1 normally gives the CISO `PT4H` (P2 `PT8H`, P3 `PT24H`). The override
   replaces the derived value.

2. Complete the two parallel tasks. Re-list → `CISO Review`. **Stop. Wait ~25 seconds.**

   **Why the wait starts here:** the timer is attached to the *task*, so it starts when CISO Review
   is created — after the parallel join, not at raise.

3. **Folder 6 → "Verify boundary & timer events"** → `SLA breach escalation (CISO review)`.
4. **Folder 3 → "List active tasks"** → `CISO Review` still open. Complete it and carry on.

### 9b — 72-hour regulatory deadline

1. **Folder 6 → "Regulatory 72h deadline escalation (timer, 20s)"**

   ```json
   { "title": "Breach notification deadline demo", "source": "SIEM",
     "regulatoryDeadline": "PT20S", "attackConfirmed": true,
     "assetCriticality": "CRITICAL", "dataExposed": true, "recordCount": 25000 }
   ```

   **Why 25000 records:** the Legal task only exists if the regulatory DMN says so. No exposure, no
   task, no timer to demonstrate.

2. Drive through the parallel pair → CISO Review → Integrity Verification. Re-list → `File
   Regulatory Notification`. **Wait ~25 seconds.**
3. **Verify boundary & timer events** → `Regulatory deadline escalation (72h)`. Task still open.

**Verified in scenario 3: both timers fire and neither cancels its task.**

**What you proved:** nobody has to remember the deadline. Both timers are non-interrupting, which is
the deliberate choice — escalation notifies without destroying the work in progress.

---

<a id="10"></a>
## Scenario 10 — Ad-hoc response actions

**The story:** real incidents don't follow a fixed script. The commander picks actions as findings
come in. The ad-hoc sub-process models professional judgement without hardcoding it.

### Steps

1. **Folder 6 → "All three ad-hoc response actions"**

   ```json
   { "title": "Full response playbook", "source": "EDR",
     "responseActions": ["Task_BlockIp", "Task_RevokeCredentials", "Task_DeployPatch"],
     "attackConfirmed": true, "assetCriticality": "CRITICAL",
     "dataExposed": false, "recordCount": 0 }
   ```

2. **Folder 6 → "Single ad-hoc action (block IP only)"** — `["Task_BlockIp"]`.

3. Drive either to CLOSED. In **Operate**, open the ad-hoc sub-process and show which inner tasks
   completed. Nothing in the REST API surfaces the individual actions, so this one is an Operate
   screenshot.

**Verified output:** the single-action variant reaches `CLOSED` — the ad-hoc branch joins on
whatever subset was activated. All-three verified via `worker_execution`, where `Task_DeployPatch`
appears only for runs that requested it.

**Why the default is two actions:** so the branch completes unattended in every other scenario. Only
these two requests change it.

**What you proved:** the branch completes on whatever subset was activated, rather than waiting for
a fixed set. Valid ids are `Task_BlockIp`, `Task_RevokeCredentials`, `Task_DeployPatch` — anything
else is a `400` (scenario 2, step 5).

---

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| **List active tasks** returns empty right after raising | Normal. The search index lags the engine. Re-run after a few seconds. |
| Still empty after 60s | Check the app log for a stuck worker, or Operate for an incident on the instance. |
| `422 AMBIGUOUS_TASK` | Two tasks open (the parallel phase). Complete by `userTaskKey`, not the active-task endpoint. |
| `422 USER_TASK_NOT_FOUND` | Stale `{{taskKey}}` — the task was already completed. Re-run **List active tasks**. |
| A timer never fires | The override only applies to incidents raised *with* it. Check you used the folder 6 request, not the folder 1 one. |
| AI never falls back when you expect it to | That means the connector is working. Add `forceAiFailure`. |
| Every incident shows an AI fallback | `OPENAI_API_TOKEN` isn't set as a cluster secret in Camunda Console. The flow still completes — see scenario 8's control run. |

## What was verified, and when

Everything in this file was run against `incident-response:10` on 2026-07-29 with PostgreSQL 17 and
the Camunda 8.9 SaaS cluster:

- all six classification paths (P1 default, P1 CRITICAL, P2, P3, P1 under-threshold, P4)
- three full runs to CLOSED covering both regulatory outcomes (six human tasks with the Legal step,
  five without)
- all five boundary events on a single incident, still reaching CLOSED
- the AI control run proving the connector succeeds when not deliberately broken
- all 14 negative tests, plus all 14 valid raise bodies still returning `201`
