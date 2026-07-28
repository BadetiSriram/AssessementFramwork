# Postman collection

Import `incident-response-service.postman_collection.json` (Import → File). It drives the whole
process and covers every path through the two DMN tables, plus the error responses.

Set `baseUrl` under the collection's Variables tab if you're not on `http://localhost:8080`, start
the backend, and keep the Postman console open (View → Show Postman Console) - the test scripts log
incident ids, task names and status there.

## Folders

| | |
| --- | --- |
| 0 | health and api-docs |
| 1 | raise an incident, one request per scenario |
| 2 | read the incident back |
| 3 | list / complete tasks, with one shared body |
| 4 | the same, but with each task's real form fields |
| 5 | validation and business errors |

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

## Errors

- Missing or blank `title`/`source` → 400 with a `fieldErrors` array.
- Unknown incident id, on the incident or the task endpoints → 422, `INCIDENT_NOT_FOUND`.
- `POST .../tasks/complete` with nothing open → 422 `NO_ACTIVE_TASK`; with two open (the parallel
  phase) → 422 `AMBIGUOUS_TASK`, complete by `userTaskKey` instead.

Successful responses are wrapped as `{"data": ..., "meta": {...}}`, which is why the test scripts
all read `data`.
