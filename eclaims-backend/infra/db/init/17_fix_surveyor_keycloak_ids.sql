-- Fix surveyor IDs to match Keycloak user IDs
-- This ensures auto-assignment uses real Keycloak user IDs

-- IMPORTANT: These are the actual Keycloak user IDs from eclaims-realm.json
-- All users now have explicit IDs set in Keycloak to ensure consistency

-- surveyor1@eclaims.test: 20000000-0000-0000-0000-000000000001
-- surveyor2@eclaims.test: 20000000-0000-0000-0000-000000000002
-- adjustor1@eclaims.test: 30000000-0000-0000-0000-000000000001
-- adjustor2@eclaims.test: 30000000-0000-0000-0000-000000000002

-- This migration is now obsolete as we've fixed the seed scripts directly
-- Keeping this file for reference and migration history
