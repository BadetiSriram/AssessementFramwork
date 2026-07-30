# What to type into each form — a plain-English guide

You do not need to know anything about cyber security to use this. Every field below tells you
what it is really asking and gives you a value you can copy as-is.

The safest way to use this document: pick a scenario, then work down it in order. Every value in a
grey box is copy-paste ready.

---

## 1. What the process actually is, in ordinary words

A security alarm goes off. Software does the obvious first steps automatically. Then six people
have to make decisions, in a fixed order, and each one needs to see what the person before them
found. That is all this process is.

The six people (the process calls them "personas"):

| Who | What they decide |
|---|---|
| **SOC analyst** | "Did we actually stop the attacker?" |
| **Forensics lead** | "How did they get in, and what did they take?" |
| **CISO** (the security boss) | "Is the leftover risk acceptable? Can we start rebuilding?" |
| **SOC analyst** again | "Are the rebuilt systems clean?" |
| **Legal & compliance** | "Do we have to tell a regulator? File it." |
| **Incident commander** | "Write up what we learned and close it." |

Each one gets a form. Each form now opens with a read-only summary of everything found so far, so
you can see the chain. You only ever *type* into the fields listed in this guide.

---

## 2. The only three moves you make in Postman

Everything is this loop. Learn it once and every scenario is the same.

**Move 1 — start an incident.** Run a request from **folder 1**. The reply looks like this:

```json
{ "data": { "id": "2b44e349-1fe4-4763-9c91-03e3f675628d", "status": "RAISED" } }
```

Copy the `id`. In Postman, open the collection → **Variables** tab → paste it into `incidentId`
→ **Save**.

**Move 2 — see whose turn it is.** Run **folder 3 → "List active tasks"**. The reply looks like:

```json
{ "data": [ { "userTaskKey": 2251799814940884, "name": "Forensic Analysis" } ] }
```

Copy the `userTaskKey` number into the `taskKey` variable → **Save**.

> Wait about 10 seconds after starting an incident before the first "List active tasks". The
> automated steps run first and the task will not exist yet. An empty `[]` means "wait, not broken".

**Move 3 — fill that person's form.** Go to **folder 4** and run the request whose name matches the
`name` you just saw. Folder 4 already contains a complete, valid body — you can run it untouched, or
edit values using the tables in this guide.

Then repeat moves 2 and 3 until there are no tasks left.

### The one situation that trips people up

Early on, **two tasks are open at the same time** (Containment Verification and Forensic Analysis).
That is deliberate — two people work in parallel in real life.

- Do **not** use "Complete active task" while two are open. It deliberately refuses with
  `422 AMBIGUOUS_TASK`, because it cannot guess which one you meant.
- Instead: set `taskKey` to the first one, run its folder-4 request, then set `taskKey` to the
  second one and run its folder-4 request.
- The order between those two does not matter.

### One thing worth knowing about "required"

Fields marked **required** in this guide are enforced by the **form in Tasklist** (the web UI) — it
will not let you submit until they are filled. Going through Postman skips that check, so a
half-filled body still succeeds. If you are demonstrating this to someone, open at least one form in
Tasklist so they see the validation working.

---

## 3. Word list — the jargon, decoded

You will meet these words in the field names. Nobody expects you to already know them.

| Term | What it means |
|---|---|
| **EDR** | The security software installed on laptops and servers. It can also cut a machine off the network. |
| **C2 / C2 beaconing** | The attacker's remote control server. "Beaconing" is the infected machine phoning home to it. "No further beaconing" = it stopped phoning home. |
| **IOC** (indicator of compromise) | A concrete clue: a bad IP address, a bad web domain, a bad file. |
| **MITRE ATT&CK technique** | An industry catalogue of attack methods, with codes like `T1566.001`. `T1566.001` means "phishing with a malicious attachment". Optional field — leave blank if unsure. |
| **Patient zero** | The very first machine or account the attacker got into. |
| **Lateral movement** | The attacker hopping from the first machine to other machines. |
| **Dwell time** | How many hours the attacker was inside before anyone noticed. |
| **Exfiltration** | Data actually being copied out of the company. |
| **PII / PCI / PHI** | Personal data / payment card data / health data. These are the categories regulators care about. |
| **Isolation / containment** | Cutting the affected machines off so the attacker cannot do more damage. |
| **Residual risk** | The risk still left over after you have contained it. Someone senior has to formally accept it. |
| **GDPR Art. 33** | The European rule that says a data breach must be reported to a regulator within 72 hours. |
| **Known-good backup** | A backup from before the attack, so restoring it does not restore the malware too. |
| **MFA** | Two-factor login. |
| **SLA** | A deadline the process enforces. If the CISO does not respond in time, it auto-escalates. |
| **P1 / P2 / P3 / P4** | Severity. P1 is worst, P4 means false alarm. The system works this out itself. |

---

## 4. Scenario 1 — the full run (start here)

**Use this one for a demo.** It is the longest path: six forms, ends with a regulator filing.

**Step 1.** Folder 1 → **"P1 - default triage signals (happy path)"**

```json
{
  "title": "Ransomware + data exfiltration on prod-db",
  "source": "SIEM"
}
```

Copy `data.id` → `incidentId` variable. Wait ~10 seconds.

**Step 2.** Folder 3 → "List active tasks". You will see **two** tasks. Do them one at a time.

---

### Form A — Forensic Analysis (the forensics lead)

Postman: folder 4 → **"Forensic Analysis (forensics-lead)"**

This is the most important form in the whole process. What you type here is shown to the CISO on the
next screen, is written into the final AI report, **and decides whether Legal has to file with a
regulator.**

| Field | What it is really asking | What to type |
|---|---|---|
| `attackVector` **(required)** | How did they get in? | `Phishing` — pick from: `Phishing`, `Stolen credentials`, `Credential stuffing`, `Exploit of public-facing application`, `Supply chain`, `Insider`, `Misconfiguration`, `Not established` |
| `mitreTechnique` | The catalogue code, if you know it. Optional. | `T1566.001` (or leave out entirely) |
| `patientZeroAsset` **(required)** | Which machine or account was hit first? Any name works. | `FIN-WS-014` |
| `dwellTimeHours` | How many hours before anyone noticed? A number. | `36` |
| `rootCause` **(required, 20+ chars)** | In a sentence or two: what went wrong? | `Phishing email delivered a loader to a finance workstation; reused local admin credentials allowed lateral movement to prod-db.` |
| `impactScope` **(required, 20+ chars)** | What was affected — and what was not? | `Finance subnet: 1 workstation, 2 database servers. Ruled out: HR and payroll segments.` |
| `iocsObserved` | The bad addresses/files you saw. Free text. | `sha256:9f2b4c...\n185.220.101.44\nevil-c2.example` |
| `exfiltrationConfirmed` **(required)** | Did data actually leave? Exactly one of three values. | `CONFIRMED` (others: `SUSPECTED`, `RULED_OUT`) |
| `dataCategories` | What kind of data was involved? A list. | `["Personal data (PII)", "Payment card data (PCI)"]` |
| `confirmedRecordCount` | How many records, now that you have checked properly? | `25000` |
| `analysisConfidence` **(required)** | How sure are you? | `High` (others: `Medium`, `Low`) |
| `evidenceCustodian` | Who is holding the evidence? | `forensics.lead` |
| `evidenceRefs` | Any case or ticket numbers. | `IMG-0091, CASE-4471` |

> **The two fields that change what happens next.** `exfiltrationConfirmed` and `dataCategories`
> together decide the `dataExposed` flag, and `confirmedRecordCount` replaces the original estimate.
> With `CONFIRMED` + at least one category + `25000`, the regulator step **will** appear later. If you
> set `exfiltrationConfirmed` to `RULED_OUT`, or leave the categories empty, it **will not**.

---

### Form B — Containment Verification (the SOC analyst)

Postman: folder 4 → **"Containment Verification (soc-analyst)"**

You are confirming the attacker is genuinely cut off.

| Field | What it is really asking | What to type |
|---|---|---|
| `containmentChecks` **(required)** | Which checks did you personally confirm? A list — tick as many as apply. | `["No further C2 beaconing", "No new process execution on EDR", "Lateral movement blocked", "Egress to IOCs blocked", "Credentials revoked"]` (also available: `Backups isolated`) |
| `observationWindowMinutes` **(required)** | How long did you watch to be sure nothing came back? Minutes. | `30` |
| `residualExposure` **(required)** | Is anything still exposed? | `None` (others: `Limited`, `Ongoing`) |
| `containmentNotes` **(required, 20+ chars)** | Your evidence, in a sentence. | `Endpoints quarantined, egress to C2 blocked, no beaconing observed for 30 minutes.` |
| `containmentVerified` **(required)** | Your sign-off. | `true` |

> `containmentVerified` must be `true`. There is no "no" path in the process — if containment did not
> work, the correct action is to leave the task open and go fix it, not to submit a `false`.

---

### Form C — CISO Review (the security boss)

Postman: folder 4 → **"CISO Review (ciso)"**

**This is the form that changed most.** When you open it in Tasklist you now see the whole incident
file on one screen — everything forensics just wrote, the containment result, and which automated
actions ran. Previously this screen was two blank checkboxes.

| Field | What it is really asking | What to type |
|---|---|---|
| `residualRiskRating` **(required)** | How bad is the leftover risk? | `Medium` (others: `Low`, `High`, `Critical`) |
| `residualRiskAccepted` | Are you formally accepting it? | `true` |
| `riskAcceptanceRationale` **(required if accepted, 20+ chars)** | Why is it acceptable? This is the audit record. | `Attacker access severed and monitored; remaining risk acceptable against the urgency of restoring prod-db.` |
| `recoveryConditions` | Rules the rebuild must follow. The analyst sees this list on the next form. | `["Restore from known-good backup only", "Rotate privileged credentials first", "MFA enforced before re-enable"]` (also: `Rebuild rather than clean`, `Enhanced monitoring for 14 days`) |
| `recoveryAuthorized` **(required)** | Do you approve starting the rebuild? | `true` |
| `externalCommsApproved` **(required)** | What do we tell the outside world? | `Approved - holding statement` (others: `Not required`, `Approved - full disclosure`, `Withheld pending legal`) |
| `boardNotificationRequired` | Does the board need telling? | `true` |
| `regulatoryAssessmentNote` | A note for Legal, who see it on their form. | `PII and PCI both in scope; assume GDPR Art. 33 applies.` |
| `cisoNotes` **(required, 20+ chars)** | Your decision and reasoning. | `Containment and forensics reviewed. Residual risk accepted; recovery authorised under the conditions above.` |

---

### Form D — Integrity Verification (the SOC analyst again)

Postman: folder 4 → **"Integrity Verification (soc-analyst)"**

The systems have been rebuilt. You are confirming they are clean. The form shows you the CISO's
`recoveryConditions` from the previous step so you can check against them.

| Field | What it is really asking | What to type |
|---|---|---|
| `integrityChecks` **(required)** | Which cleanliness checks passed? | `["Hashes match known-good baseline", "Full EDR scan clean", "No unauthorised accounts or scheduled tasks", "Privileged credentials rotated", "MFA enforced"]` (also: `Patch level verified`, `Logging and monitoring restored`) |
| `scanTool` | What did you scan with? | `CrowdStrike Falcon` |
| `scanReference` | The scan's report number. | `SCAN-8823` |
| `servicesValidated` **(required, 20+ chars)** | Which services are confirmed healthy? | `prod-db primary and replica confirmed healthy; finance workstation rebuilt from image.` |
| `monitoringPeriodDays` | How long will you watch it closely? Days. | `14` |
| `integrityNotes` | Anything else worth recording. | `Restored services scanned clean; hashes match baseline.` |
| `integrityVerified` **(required)** | Your sign-off. | `true` |

---

### Form E — File Regulatory Notification (Legal & compliance)

Postman: folder 4 → **"File Regulatory Notification (legal-compliance)"**

**This form only appears if data was exposed and 500+ records were affected.** If it does not show
up, that is correct behaviour — skip to Form F.

The form explains at the top *why* it appeared, and shows the deadline. A background timer escalates
to the CISO if the deadline passes, but it does **not** cancel the task — so file it either way.

| Field | What it is really asking | What to type |
|---|---|---|
| `regulators` **(required)** | Which authorities were told? | `["EU supervisory authority (GDPR Art. 33)", "US state attorneys general"]` (also: `UK ICO`, `SEC (Item 1.05)`, `HHS OCR (HIPAA)`, `Sectoral or other regulator`) |
| `jurisdictions` **(required)** | Which countries/states, named. | `EU (Ireland lead), US-CA` |
| `dataSubjectsNotificationRequired` **(required)** | Do the affected people need telling? | `Required` (others: `Not required`, `Under legal assessment`) |
| `filingReference` **(required)** | The reference number the authority gave back. This is your proof. | `DPC-2026-88431` |
| `filedAt` **(required)** | When you filed. Date-and-time format. | `2026-07-31T09:15:00+02:00` |
| `legalCounselApprover` | Which lawyer approved it. | `legal.counsel` |
| `lawEnforcementNotified` | Did you tell the police? | `false` |
| `lawEnforcementRef` | Their case number. Only appears if the above is `true`. | *(omit)* |
| `notificationDetails` **(required, 20+ chars)** | What you actually disclosed. | `Supervisory authority notified at T+41h; affected data subjects notified by email.` |
| `notificationFiled` **(required)** | Your sign-off. | `true` |

---

### Form F — Incident Closure (the incident commander)

Postman: folder 4 → **"Incident Closure (incident-commander)"**

Before this form, an AI writes the post-incident report from everything above. **The form now shows
you that full report** — previously the person signing off could not see it at all.

| Field | What it is really asking | What to type |
|---|---|---|
| `lessonsLearned` **(required, 30+ chars)** | What are we changing because of this? | `MFA was not enforced on finance workstations and egress filtering did not cover the C2 domain. Both are now funded work items. Detection worked as designed; the EDR isolation API failing is the main response gap.` |
| `preventableWithExistingControls` **(required)** | Could our existing tools have stopped this? | `Partially` (others: `Yes`, `No`) |
| `controlGaps` | Which protections fell short? | `["MFA coverage", "Egress filtering", "Logging and detection coverage"]` (also: `EDR coverage`, `Patch cadence`, `Backup isolation`, `User awareness`, `Asset inventory accuracy`) |
| `followUpActions` | The to-do list. A list of items, each with who owns it and when it is due. | `[{"action": "Enforce MFA on all finance endpoints", "owner": "identity.team", "dueDate": "2026-08-31"}]` |
| `mttdHours` | Hours to notice the attack. | `36` |
| `mttrHours` | Hours to fix it. | `14` |
| `postIncidentReviewDate` | When the review meeting is. Date only. | `2026-08-06` |
| `closureApprover` | Who approved closing it. | `incident.commander` |
| `closureNotes` **(required, 20+ chars)** | Final statement for the record. | `Post-incident review complete; MFA rollout and egress filtering tracked as follow-up actions.` |
| `lessonsLearnedCaptured` **(required)** | Your sign-off. | `true` |

**Step 3.** Folder 2 → "Get incident". You should see `"status": "CLOSED"`.
Folder 3 → "Task outcomes" gives you the full audit trail — six rows, one per person.

---

## 5. Scenario 2 — the short run (no regulator involved)

Same as Scenario 1 but **Form E never appears**, because no data was exposed. Four forms instead of
six. Good for showing that the decision table actually decides something.

**Start with** folder 1 → **"P2 - HIGH asset, no data exposed"**:

```json
{
  "title": "Contained breach, no data exfiltration",
  "source": "SIEM",
  "attackConfirmed": true,
  "assetCriticality": "HIGH",
  "dataExposed": false,
  "recordCount": 0
}
```

Then: Forensic Analysis → Containment Verification → CISO Review → Integrity Verification →
**Incident Closure**. No Legal step.

> **Careful here.** If you use the standard folder-4 forensic body, it contains
> `exfiltrationConfirmed: "CONFIRMED"` and two data categories — which will **flip this incident to
> "data was exposed"** and the Legal form *will* appear. That is the feature working correctly, not a
> bug. To keep this scenario short, change those two fields:
>
> ```json
> "exfiltrationConfirmed": "RULED_OUT",
> "dataCategories": []
> ```
>
> Or simply delete both fields from the body — leaving them out keeps whatever the incident started
> with.

---

## 6. Scenario 3 — the false alarm (no forms at all)

Folder 1 → **"P4 - false positive (auto-close)"**

```json
{
  "title": "Benign alert - false positive",
  "source": "SIEM",
  "attackConfirmed": false,
  "assetCriticality": "LOW",
  "dataExposed": false,
  "recordCount": 0
}
```

Nothing to fill in. `attackConfirmed: false` means the system classifies it P4 and closes it by
itself. Check folder 2 → "Get incident": `"status": "AUTO_CLOSED"`. No human is ever bothered.

---

## 7. Scenario 4 — automatic containment fails (one extra form)

This is the interesting failure case: the software tries to cut the machines off, cannot, and hands
it to a human instead.

Folder 1 → **"Isolation failure (BPMN error escalation)"**

```json
{
  "title": "Isolation-failure demo",
  "source": "SIEM",
  "forceIsolationFailure": true,
  "attackConfirmed": true,
  "assetCriticality": "CRITICAL",
  "dataExposed": true,
  "recordCount": 25000
}
```

You will now see **"Handle Isolation Failure"** instead of Containment Verification. Fill it first;
Containment Verification appears afterwards.

### Extra form — Handle Isolation Failure (the incident commander)

Postman: folder 4 → **"Handle Isolation Failure (incident-commander)"**

| Field | What it is really asking | What to type |
|---|---|---|
| `isolationFailureReason` **(required)** | Why couldn't the software do it? | `EDR agent unreachable` (others: `Host offline`, `API call failed`, `Credential failure`, `Asset not in inventory`, `Change freeze`, `Other`) |
| `containmentMethod` **(required)** | How did you do it by hand? | `Network ACL + switch port disable` (others: `EDR host quarantine`, `Powered off`, `Snapshot + suspend`, `Account disable only`) |
| `isolationFailureAction` **(required, 20+ chars)** | What you actually did, in words. | `Automated isolation failed; hosts pulled from the network manually and switch ports disabled.` |
| `assetsIsolatedCount` | How many machines you cut off. | `2` |
| `isolationCompletedAt` | When you finished. Date-and-time. | `2026-07-30T14:02:00+02:00` |
| `manuallyContained` **(required)** | Confirm they are now contained. | `true` |
| `businessImpactAccepted` | Did cutting them off break a service, and did the owner accept that? | `true` |
| `serviceOwnerApprover` | Who accepted it. Only appears if the above is `true`. | `finance.service.owner` |

**Then continue as normal.** When you open Containment Verification next, its summary panel now shows
an amber block with your reason, method and action text — so the analyst verifies *your* work rather
than the software's. That is the chain this change was about.

---

## 8. Scenario 5 — the AI is unavailable

Folder 6 → **"AI failure on both steps -> drive to CLOSED (P2)"**. Nothing extra to fill in — the
forms are identical. The point is that the process still reaches CLOSED, with plain-worker text
instead of AI text. The forensic form's summary panel will say *"AI unavailable — recorded without
enrichment"*, and the closure form's report section will show the simple fallback report.

---

## 9. Scenario 6 — deadlines expiring

Both use a shortened timer so you do not wait hours.

- Folder 6 → **"CISO SLA breach escalation (timer, 20s)"** — start it, then just wait. After 20
  seconds an escalation fires even though CISO Review is still open. Open the CISO form afterwards
  and a red banner says the SLA was breached. The task is *not* cancelled — you can still complete it.
- Folder 6 → **"Regulatory 72h deadline escalation (timer, 20s)"** — same idea on the Legal form.
- Folder 6 → **"Every boundary event in one run (full sweep)"** — all five failure paths on one
  incident. Fill forms exactly as in Scenario 4, and check folder 6 → "Verify boundary & timer
  events" at the end.

---

## 10. Format reminders for the fiddly field types

| Type | Format | Example |
|---|---|---|
| Yes/no (checkbox) | `true` or `false`, no quotes | `"recoveryAuthorized": true` |
| Number | digits, no quotes | `"dwellTimeHours": 36` |
| Pick-one (radio/select) | quoted text, **exactly** one of the listed values | `"analysisConfidence": "High"` |
| Pick-many (checklist) | a list of quoted values | `"controlGaps": ["MFA coverage", "Patch cadence"]` |
| Date and time | `YYYY-MM-DDTHH:MM:SS+HH:MM` | `"filedAt": "2026-07-31T09:15:00+02:00"` |
| Date only | `YYYY-MM-DD` | `"postIncidentReviewDate": "2026-08-06"` |
| Follow-up actions | list of objects | `[{"action": "...", "owner": "...", "dueDate": "2026-08-31"}]` |

For pick-one and pick-many, the value must match the list in this guide character for character. A
value that does not match is silently ignored rather than rejected, so the field just looks empty.

---

## 11. If something goes wrong

| What you see | What it means | What to do |
|---|---|---|
| `"data": []` from "List active tasks" | The automated steps have not finished yet, or the incident is closed. | Wait 10 seconds, try again. Then check "Get incident" for the status. |
| `422 AMBIGUOUS_TASK` | Two tasks are open and you used "Complete active task". | Use folder 4 with `taskKey` set, one task at a time. |
| `422 NO_ACTIVE_TASK` | Nothing is waiting on a human. | Check "Get incident" — it is probably already CLOSED. |
| `422 USER_TASK_NOT_FOUND` | Your `taskKey` is stale — that task is already done. | Re-run "List active tasks" and copy the new key. |
| `422 INCIDENT_NOT_FOUND` | The `incidentId` variable is wrong or empty. | Re-copy `data.id` from the raise response. |
| `400` with field names | A value has the wrong shape (e.g. text where a number belongs). | Check the format table in section 10. |
| The Legal form never appeared | No data exposure, or fewer than 500 records. | Correct behaviour. See the note in Scenario 2. |
| Nothing responds at all | The service is not running. | `mvn spring-boot:run "-Dspring-boot.run.profiles=local"` from `incident-response-service`. |

---

## 12. The short version

1. Folder 1 → start an incident → copy `data.id` into `incidentId`.
2. Folder 3 → "List active tasks" → copy `userTaskKey` into `taskKey`.
3. Folder 4 → run the request matching the task name.
4. Repeat 2–3 until nothing is left.
5. Folder 2 → confirm `CLOSED`. Folder 3 → "Task outcomes" for the audit trail.

The folder-4 bodies are already complete and valid. You can run the whole thing without editing a
single field — this guide is for when you want to know what you are saying.
