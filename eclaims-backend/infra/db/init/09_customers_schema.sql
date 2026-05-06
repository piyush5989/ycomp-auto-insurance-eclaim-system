-- Customers Schema
-- Stores customer self-service profile preferences.
-- Identity (authentication) is owned by Keycloak — this table stores mutable domain data only.
-- customer_id = Keycloak subject (UUID string from JWT sub claim)

CREATE SCHEMA IF NOT EXISTS customers;

CREATE TABLE IF NOT EXISTS customers.customer_profiles (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     VARCHAR(100)    NOT NULL UNIQUE,   -- Keycloak subject / JWT sub
    phone           VARCHAR(20),
    address_line1   VARCHAR(200),
    address_line2   VARCHAR(200),
    city            VARCHAR(100),
    state           VARCHAR(100),
    zip_code        VARCHAR(20),
    country         VARCHAR(100)    DEFAULT 'US',
    billing_cycle   VARCHAR(20)     NOT NULL DEFAULT 'MONTHLY',   -- MONTHLY | QUARTERLY | ANNUALLY
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customer_profiles_customer_id
    ON customers.customer_profiles(customer_id);
