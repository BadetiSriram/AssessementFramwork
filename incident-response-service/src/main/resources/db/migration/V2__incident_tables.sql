-- Consumer-owned schema for the incident-response service (Use Case 4).
-- ddl-auto=validate, so this must match the Incident @Entity mapping exactly.
-- severity is nullable: it is set only after the classification DMN runs.

CREATE TABLE incidents (
    id            UUID         PRIMARY KEY,
    business_key  VARCHAR(200) NOT NULL,
    title         VARCHAR(300) NOT NULL,
    source        VARCHAR(100) NOT NULL,
    severity      VARCHAR(10),
    status        VARCHAR(30)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100)
);

CREATE INDEX idx_incidents_business_key ON incidents (business_key);
CREATE INDEX idx_incidents_status ON incidents (status);
