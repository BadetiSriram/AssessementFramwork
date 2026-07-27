-- Store the Camunda process instance key on the incident (to find/complete its user tasks),
-- and record the outcome of every human task (form data) that gets completed.

ALTER TABLE incidents ADD COLUMN process_instance_key BIGINT;

CREATE TABLE incident_task_outcomes (
    id                    UUID          PRIMARY KEY,
    incident_id           UUID          NOT NULL,
    user_task_key         BIGINT        NOT NULL,
    element_id            VARCHAR(200),
    task_name             VARCHAR(300),
    completed_by          VARCHAR(200),
    outcome               TEXT,          -- JSON of the submitted form variables
    created_at            TIMESTAMP      NOT NULL
);

CREATE INDEX idx_task_outcomes_incident ON incident_task_outcomes (incident_id);
