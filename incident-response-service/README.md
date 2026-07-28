# incident-response-service

Cyber security incident response on Camunda 8.9, built on `camunda-process-framework` (same shape
as `order-service-sample`). Automated steps are idempotent Spring Boot job workers; human steps are
native Camunda user tasks with forms.

Requirements and background: `../usecase-4/UC4-REQUIREMENTS.md`.

## Layout

```
web/                              controllers + DTOs
application/                      IncidentService, IncidentTaskService (@Transactional lives here)
domain/                           Incident aggregate + state machine, IncidentTaskOutcome
repository/                       the two JPA repositories
infrastructure/camunda/           CamundaClient adapter + deployment config
infrastructure/camunda/worker/    13 job workers
resources/processes/              incident-response.bpmn
resources/dmn/                    incident-classification.dmn, regulatory-notification.dmn
resources/forms/                  one .form per user task
resources/db/migration/           V1 framework tables, V2 incidents, V3 task outcomes
```

ArchUnit keeps the layering honest: only `infrastructure.camunda` may import `io.camunda.client`,
controllers can't be transactional or return entities, and the domain stays free of Spring Web.

## The process

SIEM alert starts it. AI triage runs, a DMN sets severity, and P4 auto-closes on the spot. Anything
worse fans out into four parallel streams (containment, forensics, notification, and an ad-hoc
sub-process the commander drives), joins for CISO review, recovers, checks whether Legal has to
file, writes a report and closes.

Worker types: `triage-threat`, `record-classification`, `auto-close`, `isolate-systems`,
`collect-evidence`, `notify-stakeholders`, `escalate`, `restore-services`, `generate-report`,
`close-incident`, plus the ad-hoc `block-ip`, `revoke-credentials`, `deploy-patch`.

The seven user tasks each have a candidate group: `soc-analyst`, `forensics-lead`, `ciso`,
`legal-compliance`, `incident-commander`. The BPMN pool has a lane per group so you can see the
hand-offs.

## Running it

You need Java 21, Maven, the framework installed to `~/.m2` (`mvn install -DskipTests` in the
framework repo), PostgreSQL 17 with an `incident_response` database, and Camunda 8.9 SaaS
credentials in `application-local.yml`.

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"   # the -D arg needs quoting in PowerShell
```

Startup should show 3 migrations applied, the process and both decisions deployed, and 13 workers
registered.

```powershell
curl -X POST http://localhost:8080/incidents -H "Content-Type: application/json" -d '{\"title\":\"Lateral movement on prod-db\",\"source\":\"SIEM\"}'
curl http://localhost:8080/incidents/<id>
```

Status walks RAISED -> TRIAGED -> CLASSIFIED. Add `"forceIsolationFailure": true` to the raise body
to take the escalation path instead. Swagger is at `/swagger-ui/index.html`.

The `postman/` collection covers every severity path and the error responses; see its README for
the run order.

## Human tasks

Complete them in Tasklist, or through the API if you want the outcomes stored:

```
GET  /incidents/{id}/tasks
POST /incidents/{id}/tasks/{userTaskKey}/complete   {"completedBy":"...","variables":{...}}
POST /incidents/{id}/tasks/complete                 only when exactly one task is open
GET  /incidents/{id}/tasks/outcomes
```

Going through the API writes a row to `incident_task_outcomes` with whatever was submitted, which
survives Camunda's history cleanup. Tasklist completions don't (a `completing` task listener would
fix that, but it isn't built yet).

## AI steps

Threat triage and the post-incident report are AI Agent connector tasks
(`io.camunda.agenticai:aiagent:1`) against `gpt-4o-mini`. Auth comes from the
`OPENAI_API_TOKEN` cluster secret, set in Console under Connector secrets.

Both have an error boundary that falls back to a plain job worker, so a missing key or an OpenAI
outage degrades the output instead of stalling the incident. That's also why you can run the whole
demo without configuring the secret at all.

## What's done

Everything above works end to end. A P1 has been driven all the way to CLOSED through the task API
with all six outcomes persisted; P4 auto-close, the isolation-failure escalation, and the AI
fallback have all been exercised. Both escalation timers are modelled (severity-based SLA on CISO
review, 72 hours on the regulatory task) and fire in Operate.

Still open:
- Task listener so Tasklist-channel completions record outcomes too.
- Real integrations behind the workers; they're stubs that return a flag.
- SLA durations and AI prompts are first-draft guesses.

> Don't commit `*.env`. It has live API keys.
