# Postman collection

Import `incident-response-service.postman_collection.json` (Import → File). It drives the whole
process and covers every path through the two DMN tables, plus the error responses.

Set `baseUrl` under the collection's Variables tab if you're not on `http://localhost:8080`, start
the backend, and keep the Postman console open (View → Show Postman Console) - the test scripts log
incident ids, task names and status there.

**[POSTMAN-RUN.md](POSTMAN-RUN.md) is the run-book**: ten scenarios, step by step, with the exact
request to hit, why each input is what it is, and the verified output. Start there if you're driving
a demo. This file is the reference for what's in the collection.

## Folders

| | |
| --- | --- |
| 0 | health and api-docs |
| 1 | raise an incident, one request per scenario |
| 2 | read the incident back |
| 3 | list / complete tasks, with one shared body |
| 4 | the same, but with each task's real form fields |
| 5 | validation and business errors |
| 6 | boundary events, escalation timers, ad-hoc variants |

## Happy path

Raise a P1 (folder 1, first request). Wait 25-30 seconds for the automated chain to work through AI
triage, the DMN, and the parallel branches. Then list active tasks - that saves `{{taskKey}}` and
`{{taskKey2}}`.

The parallel phase has two tasks open at once, Containment Verification and Forensic Analysis, so
complete both by key. Every phase after that has a single task, so "Complete active task" is
enough. Re-list between phases until it comes back empty:

```
[Containment + Forensic] → CISO Review → Integrity Verification → File Regulatory Notification → Incident Closure
```

Then Get incident shows CLOSED and Task outcomes has a row per task.

Folder 3's bodies send every form field at once, which works because Camunda ignores the ones a
given task doesn't ask for. Folder 4 is the honest version - one request per task with only its own
fields. Those all target `{{taskKey}}`, so list first and fire the one matching the logged name.

## Scenarios

`POST /incidents` needs `title` and `source`. The four triage signals are optional and steer the
DMNs; omit them and you get `true / HIGH / true / 25000`.

| Request | What you're sending | What happens |
| --- | --- | --- |
| P1 - default | nothing | P1, full response, Legal has to file |
| P1 - CRITICAL asset | `CRITICAL`, exposed, 120000 | P1 via the CRITICAL rule |
| P2 - no data exposed | `HIGH`, `dataExposed:false` | P2, regulatory task skipped |
| P3 - MEDIUM | `MEDIUM`, exposed, 900 | P3, still a full response |
| P1 - under threshold | `HIGH`, exposed, `recordCount:120` | P1, but regulatory skipped |
| P4 - false positive | `attackConfirmed:false` | auto-closes, no human tasks at all |
| Isolation failure | `forceIsolationFailure:true` | BPMN error, commander gets Handle Isolation Failure |

Those last two DMN rows are the pair worth showing: severity and "does Legal have to file" are
separate decisions, so the under-threshold request stays P1 while still skipping the filing task.

## Boundary events and timers (folder 6)

The model has five boundary events. Waiting for the real ones means an LLM outage and a 72-hour
clock, so `POST /incidents` takes a few override fields that make each one reachable in a demo.

| Boundary | On | Field | Effect |
| --- | --- | --- | --- |
| Isolation Failed (error) | Isolate Systems | `forceIsolationFailure:true` | commander gets Handle Isolation Failure |
| AI failed (error) | AI Threat Triage | `forceAiFailure:true` | falls back to the triage worker |
| AI failed (error) | AI Post-Incident Report | `forceAiFailure:true` | falls back to the generate-report worker |
| SLA breach (timer) | CISO Review | `slaDuration:"PT20S"` | escalation instead of the severity default (P1 PT4H) |
| 72h deadline (timer) | File Regulatory Notification | `regulatoryDeadline:"PT20S"` | escalation instead of PT72H |

`forceAiFailure` points the AI Agent tasks at a model id OpenAI rejects, so the connector really
fails and `errorExpression` really raises `AI_STEP_FAILED` - it's not a stubbed flag. Both timers
are non-interrupting: the user task stays open and you complete it as normal afterwards.

`responseActions` picks which ad-hoc actions the commander activates - any subset of
`Task_BlockIp`, `Task_RevokeCredentials`, `Task_DeployPatch`. The default is the first two, so pass
all three if you want Deploy Patch to run.

Everything automated lands in `GET /incidents/{id}/tasks/outcomes` with `completedBy` set to
`system:process` and `userTaskKey` 0, next to the human completions. "Verify boundary & timer
events" reads that back and asserts on it. The isolation-failure boundary is the exception: it
produces a real user task, so it shows as a human row.

"Every boundary event in one run" does all five on a single incident and still reaches CLOSED,
which is the point - none of these failures stop the process.

## Errors

14 negative tests in folder 5, all verified against a running service.

Bean validation → **400** with a `fieldErrors` array:

- missing or blank `title`/`source`
- `assetCriticality` outside LOW/MEDIUM/HIGH/CRITICAL - otherwise it falls through to the DMN's
  catch-all rule and comes back P3, which reads like the table is wrong rather than the request
- negative `recordCount`
- `slaDuration` / `regulatoryDeadline` that aren't ISO-8601 - these become BPMN timer expressions,
  so a bad one fails the process instance minutes later instead of failing the request
- an entry in `responseActions` that isn't one of the three ad-hoc element ids; the error names the
  index, e.g. `responseActions[1]`

Business errors → **422** with a stable `errorCode`:

- `INCIDENT_NOT_FOUND` - unknown incident id, on the incident or the task endpoints
- `NO_ACTIVE_TASK` / `AMBIGUOUS_TASK` - `POST .../tasks/complete` with nothing open, or with two
  open during the parallel phase (complete by `userTaskKey` instead)
- `USER_TASK_NOT_FOUND` - a `userTaskKey` that doesn't exist or was already completed

Unparseable input → **400** with `rejectedValue`:

- malformed JSON body, or a field of the wrong type (`"recordCount": "lots"`)
- a path id that isn't a UUID - distinct from a well-formed UUID that doesn't exist, which is 422

Those last three used to come back as **500 "An unexpected error occurred."** The framework's
handler covers validation and its own exception types, but Jackson parse failures and path-variable
conversion failures fell through to its `Exception` catch-all. `IncidentWebExceptionHandler` is
ordered ahead of it and claims those two types; the Camunda 404 is translated in
`CamundaTaskAdapter`, which keeps the Camunda client inside `infrastructure.camunda` where the
ArchUnit rule requires it.

Successful responses are wrapped as `{"data": ..., "meta": {...}}`, which is why the test scripts
all read `data`.
