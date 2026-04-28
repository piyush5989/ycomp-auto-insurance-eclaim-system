-- eClaims Database Schema Initialization
-- Each schema corresponds to one bounded-context module.
-- Rule: A module's persistence layer may ONLY access its own schema.
-- Cross-module data access is via API calls or event consumption only.

CREATE SCHEMA IF NOT EXISTS claims;
CREATE SCHEMA IF NOT EXISTS documents;
CREATE SCHEMA IF NOT EXISTS workflow;
CREATE SCHEMA IF NOT EXISTS workshops;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS reporting;
CREATE SCHEMA IF NOT EXISTS audit;

-- Confirm schemas created
SELECT schema_name FROM information_schema.schemata
WHERE schema_name IN ('claims','documents','workflow','workshops','payments','reporting','audit');
