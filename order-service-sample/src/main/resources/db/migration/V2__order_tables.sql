-- Consumer-owned schema for the order-service sample.
-- Column names are lowercase snake_case to match Hibernate's default naming strategy
-- (businessKey -> business_key, createdAt -> created_at, ...). Because
-- spring.jpa.hibernate.ddl-auto=validate, this table must match the Order @Entity mapping.
--
-- Filename obeys the framework's Flyway naming rule: ^V\d+(_\d+)*__[a-z0-9_]+\.sql$
-- (uppercase V, then a lowercase-only description).

CREATE TABLE orders (
    id            UUID         PRIMARY KEY,
    business_key  VARCHAR(200) NOT NULL,
    product_sku   VARCHAR(100) NOT NULL,
    quantity      INTEGER      NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100)
);

CREATE INDEX idx_orders_business_key ON orders (business_key);
