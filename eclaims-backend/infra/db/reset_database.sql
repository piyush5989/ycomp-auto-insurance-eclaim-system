-- eClaims Database Reset Script
-- This script safely drops all schemas and recreates a clean database
-- USE WITH CAUTION: This will delete ALL data

-- Drop all schemas in the correct order (considering dependencies)
DROP SCHEMA IF EXISTS notifications CASCADE;
DROP SCHEMA IF EXISTS customers CASCADE;
DROP SCHEMA IF EXISTS reporting CASCADE;
DROP SCHEMA IF EXISTS audit CASCADE;
DROP SCHEMA IF EXISTS payments CASCADE;
DROP SCHEMA IF EXISTS workshops CASCADE;
DROP SCHEMA IF EXISTS workflow CASCADE;
DROP SCHEMA IF EXISTS documents CASCADE;
DROP SCHEMA IF EXISTS claims CASCADE;

-- Confirm all schemas are dropped
SELECT schema_name FROM information_schema.schemata
WHERE schema_name IN ('claims','documents','workflow','workshops','payments','reporting','audit','customers','notifications');