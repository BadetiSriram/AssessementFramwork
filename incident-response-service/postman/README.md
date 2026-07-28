# Postman collection — Incident Response Service (UC4)

Import `incident-response-service.postman_collection.json` into Postman
(**Import → File**). It drives the whole `incident-response` process end-to-end.

## Setup
- The collection variable **`baseUrl`** defaults to `http://localhost:8080` (edit it under the
  collection's **Variables** tab if your service runs elsewhere).
- Start the backend first: `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`.

## Run order
1. **1 - Start → Raise incident (P1 happy path)** — starts the process; the test script saves
   `{{incidentId}}` automatically.
2. Wait **~25–30s** for the automated chain (AI triage → DMN classify → parallel branches) to reach
   the human tasks.
3. **3 - Human tasks → List active tasks** — saves `{{taskKey}}` and `{{taskKey2}}` and logs the task
   names to the Postman console.
4. Complete the tasks:
   - The **parallel phase** has **two** tasks (Containment Verification + Forensic Analysis) → run
     **Complete task by key** then **Complete 2nd task by key**.
   - Every later phase has **one** task → run **Complete active task**.
5. Re-run **List active tasks** between phases and repeat until it returns `[]`. Phase order:
   `[Containment + Forensic] → CISO Review → Integrity Verification → File Regulatory Notification → Incident Closure`.
6. **2 - Get incident** shows `status = CLOSED`; **Task outcomes** shows the persisted results.

To try the exception path, start with **Raise incident (isolation-failure path)** instead — the
first task becomes **Handle Isolation Failure** (Incident Commander).

## Classification variants (request-driven DMN)
The triage signals are optional fields on `POST /incidents` and steer the Incident Classification
DMN, so you can demo every severity path:
- **Raise incident (P1 happy path)** — no signals → defaults to **P1** (full response).
- **Raise incident (P4 - false positive, auto-close)** — `attackConfirmed:false` → **P4** → the
  incident **auto-closes** (status `AUTO_CLOSED`), no human tasks. Confirm with **Get incident**.
- **Raise incident (P2 - contained, no data loss)** — `assetCriticality:"HIGH", dataExposed:false`
  → **P2**; drive it to CLOSED via the human tasks. Because `dataExposed=false`, the Regulatory
  Notification DMN returns false and the **File Regulatory Notification** task is skipped.
- (Send `assetCriticality:"MEDIUM"` for **P3**.)

## Notes
- The complete-task requests send a **superset of form fields**; extra fields are ignored by Camunda,
  so the same body works for every task.
- Watch the **Postman Console** (View → Show Postman Console) for the logged ids, task names, and
  status.
