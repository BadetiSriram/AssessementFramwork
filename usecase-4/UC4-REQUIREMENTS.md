# Use Case 4 — Cyber Security Incident Response (Camunda 8.9)

**Assigned to:** Sriram Badeti
**Source:** `Camunda_8_9_Advanced_Process_Orchestration_Assessment_v2.docx` (§7)
**Status:** Requirements captured — implementation not started yet.
**Timebox:** 5 working days from assignment; implementation review starts Wednesday morning; ends in a 30-minute walkthrough demoed end-to-end through Tasklist.

> This document captures the requirements, rules, and implementation approach so work can begin in a
> later session. It is the durable record of my understanding of UC4.

---

## 1. Ground rules (apply to every use case — §1)

1. **Use the provided base framework** (`camunda-process-framework`) for all backend — do NOT scaffold a new project. Follow its hexagonal layering, error-handling, observability conventions.
2. **All human tasks through Camunda Tasklist** with proper forms + candidate groups. Do NOT simulate task completion via API in the demo. (Our `/orchestration/user-tasks` helper APIs are fine for *testing*, but the graded demo must use Tasklist UI.)
3. **All service tasks = Spring Boot 4.x job workers.** Connectors only where explicitly called for (AI steps, notifications).
4. **DMN tables must be deployed and invoked from the process** (business rule tasks). Hardcoded rule logic in workers is marked down.
5. Conceptual diagrams are intentionally NOT BPMN — translating them into correct BPMN is graded.
6. "Selects only the tasks needed" ⇒ an **ad-hoc sub-process**, not exclusive-gateway chains.

**Diagram legend:** blue = job worker · green = Tasklist human task · purple = AI connector step · orange diamond = DMN decision · dashed container = sub-process (embedded/call — justify) · dotted-pink container = ad-hoc sub-process.

**UC4 capability emphasis:** DMN ×2 · sub-processes ✓ · ad-hoc ✓ · AI connectors ×2 · parallel ✓ · **Timers & escalation ✓ (UC4's CORE)** · Tasklist ✓ · job workers ✓. No message/event, no compensation required.

---

## 2. UC4 requirements (§7)

### Background & objective
Enterprise SOC gets thousands of alerts/day; runbooks live in wikis, response actions are manual, and regulatory notification deadlines (72h in several jurisdictions) are tracked in email. Target: AI-triage alerts, classify severity via business rules, run containment & forensics in parallel, let the incident commander invoke response actions dynamically, and enforce the regulatory notification decision before closure. **Time-bound escalation is central.**

### Personas (→ Tasklist candidate groups)
| Persona | Role | Tasklist task(s) |
|---|---|---|
| **SOC Analyst** | Front-line responder; verifies containment | Containment verification |
| **Incident Commander** | Owns incident; selects response actions; closes incident | Activates ad-hoc response actions; closure |
| **Forensics Lead** | Analyzes evidence, establishes scope/root cause | Forensic analysis |
| **CISO (Manager)** | Reviews incident, accepts residual risk, authorizes recovery | CISO review |
| **Legal / Compliance Officer** | Files regulatory & customer notifications | Regulatory notification |

### Business flow (10 steps)
1. Security alert from **SIEM** starts the process.
2. **AI step** triages the threat (enrichment, IOC lookups, attack-pattern matching) → structured triage report.
3. **DMN** classifies incident **P1–P4** from triage output. P4 / false positives are logged and auto-closed.
4. For **P1–P3**, three streams run in **parallel**:
   - **Containment** sub-process: automated isolation → SOC analyst verification (human).
   - **Forensics** sub-process: evidence collection → forensics lead analysis (human).
   - Automated **stakeholder notification**.
5. Throughout, the **Incident Commander** uses an **ad-hoc sub-process** to invoke response actions as findings emerge: block malicious IPs/domains, revoke compromised credentials, deploy emergency patch — **invocable multiple times**.
6. **CISO** reviews the consolidated file, accepts residual risk, authorizes recovery (human).
7. **Recovery** sub-process restores services from a clean state → human task verifies system integrity.
8. **DMN** decides whether **regulatory notification is required** (data categories, record counts, jurisdictions). If yes, Legal files notices — **must respect the 72-hour deadline with timer-based escalation**.
9. **AI step** generates the post-incident report and lessons learned.
10. Incident Commander reviews the report and **closes the incident**.

### Implementation expectations (graded checklist)
- **Sub-processes:** Containment, Forensics, Recovery; a parallel split for the P1–P3 streams.
- **DMN (≥2):** *Incident Classification (P1–P4)* + *Regulatory Notification Required*; classification drives downstream SLAs; hit policies chosen and justified.
- **AI Connector (2):** threat triage at intake; post-incident report at the end.
- **Ad-hoc sub-process:** response actions available for the response duration; actions may repeat.
- **Timers & escalation (CORE):** 72h regulatory-deadline timer with escalation to CISO; **severity-based SLA timers on key human tasks**.
- **Tasklist forms + candidate groups:** containment verification, forensic analysis, CISO review, regulatory notification, integrity verification, closure.
- **Idempotent Spring Boot 4.x workers (re-delivery safe):** SIEM ingestion, isolation actions, evidence collection, IP blocking, credential revocation, restore automation.
- **Error handling:** failed isolation raises a **BPMN error that escalates to the incident commander** — no silent infinite retry.

### Deliverables
BPMN 2.0 (all sub-processes + ad-hoc) · DMN 1.3 (deployed, invoked via business rule tasks) · AI Connector config (prompt design + output var mapping documented) · Tasklist forms per persona · Spring Boot 4.x workers on the framework · error handling (BPMN errors, retries, escalation) · testing evidence (≥1 happy path + ≥1 exception path in Operate) · 1–2 page design note.

---

## 3. Evaluation rubric (§9)
| Criterion | Weight |
|---|---|
| Process modeling quality | 20% |
| DMN design | 15% |
| AI Connector usage | 15% |
| Ad-hoc sub-process | 10% |
| Spring Boot 4.x workers | 20% |
| Error handling & resilience | 10% |
| Tasklist & personas | 5% |
| Testing evidence & documentation | 5% |

**Submission:** export BPMN+DMN from Web Modeler; commit with worker code under my name; include a 1–2 page design note (sub-process choices, DMN hit policies, AI prompt design, error-handling strategy); Operate evidence (happy + exception path); be ready for a 30-min walkthrough demoing through Tasklist.

---

## 4. Mapping to the base framework

**Framework provides:** worker execution model, error classification, idempotency, outbox, state machine, layering rules, scaffold. **Pure Camunda modeling (framework adds nothing):** DMN, AI connectors, timers/escalation, ad-hoc sub-processes.

- **Job workers** → `BaseWorker<V>` subclasses under `infrastructure/camunda/worker` (ArchUnit rule #2). Implement `varsType()` + `doWork(V, ActivatedJob)`; ctor `(VariableMapper, IdempotencyGuard, MeterRegistry)`; `@JobWorker(type="…", autoComplete=false)` → `execute(...)`. **`type` must exactly match the BPMN `<zeebe:taskDefinition type>`.** Reference: sample's `ReserveInventoryWorker` / `TestJobWorker`.
- **Idempotency (UC4 hard requirement)** → put a `businessKey` (= `incidentId`) in every worker's vars; `IdempotencyGuard` short-circuits replays via the `worker_execution` table. It's non-atomic (reduces, not eliminates, duplicates) and no-ops if `businessKey` is absent — so also make each `doWork` naturally idempotent (isolation, IP-block, revoke, restore).
- **Error handling** →
  - Failed isolation → `WorkResult.businessError("ISOLATION_FAILED", msg)` (or throw `BusinessException`/`NonRetryableException`) → framework `newThrowErrorCommand` → **BPMN error boundary event** routes to the commander.
  - Transient/technical → throw `RetryableException` → rethrown → Camunda consumes a retry → **incident** in Operate.
  - `errorCode` is a 3-way contract (BPMN error code = Micrometer tag = RFC-7807 code).
  - **Retry counts/backoff live in the BPMN `<zeebe:taskDefinition retries=…>`, not the framework.**
- **Human tasks** → framework `ProcessService` facade has `completeActiveUserTask` / `findActiveUserTaskKey` (we also built equivalents in `order-service-sample`), but the **graded demo completes tasks in Tasklist UI**.
- **AI steps** → Camunda **AI connector** (preferred) or a `BaseWorker` calling an external AI service.
- **Deploy BPMN/DMN** → reuse the `@Deployment(resources="classpath*:processes/*.bpmn")` we wired into `order-service-sample` (`CamundaDeploymentConfig`, via the Camunda SDK `DeploymentAnnotationProcessor`); add DMN similarly. Resources under `src/main/resources/processes/` (+ `dmn/`). Service Flyway starts at `V2__`.
- **Consuming service** → reuse the existing **`order-service-sample`** (framework already wired, Postgres/H2 profiles, deployment config, orchestration/user-task helpers) rather than a fresh `service-template` copy. Decide: `usecase4` package vs new module. Add the 6-rule ArchUnit test. Stack pinned (Java 21, Boot 4.0.5, Camunda 8.9.0, **Jackson 2.x**). Our framework `.m2` copy already has the auto-config-ordering fix (defect #7).

---

## 5. Open questions for the lead (ask early — interpretation is graded, blocked time is not)
1. **Repo/branch** for committing UC4 (a `usecase-4/` folder under my name in which repo?).
2. **AI connector/provider** licensed on the SaaS cluster (OpenAI / Anthropic / Bedrock)?
3. **BPMN ownership** — Aditya Zade prepares a UC4 BPMN; decision taken: I model my own (rubric grades my design) and reconcile with his later.
4. **Cluster sharing** — shared 8.9 SaaS cluster (`sin-1`, `0fc1dec6-…`) or separate? (avoid process-id collisions).
5. **Stakeholder notification** — connector or worker? (Rule 3 permits a connector for notifications.)

---

## 6. Next-session implementation steps
0. (Done) Persist this doc + memory.
1. **Model my own BPMN** (Web Modeler): SIEM start → AI triage → DMN classify → (P4 auto-close) / parallel [Containment, Forensics, Notification] → ad-hoc response actions (commander) → CISO review → Recovery → DMN regulatory-required → (72h timer + escalation to CISO) → AI post-incident report → commander closure. Severity-based SLA timers on key human tasks; BPMN error on failed isolation → commander.
2. **DMN** ×2: Incident Classification (P1–P4) + Regulatory Notification Required; inputs from AI triage + process vars; justify hit policies.
3. **Workers** (idempotent `BaseWorker`): SIEM ingestion, isolation, evidence collection, IP/domain block, credential revoke, restore (+ notification if a worker). `businessKey=incidentId`; `businessError` for isolation failure; bounded retries for transient.
4. **AI connectors** ×2: triage + post-incident report; prompts using process vars; structured output mapped to vars; failure/timeout handling.
5. **Tasklist**: forms + candidate groups for the 6 human tasks / 5 personas.
6. **Test + document**: Operate happy path + one exception (failed isolation escalation, or 72h timer); 1–2 page design note; prep 30-min walkthrough.

**Verification:** `mvn test` (ArchUnit rules pass) · deploy BPMN/DMN to 8.9 SaaS · happy-path P1 instance + one exception path in Operate · complete every human task via **Tasklist** for the demo.
