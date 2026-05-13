-- eClaims Development Seed Data
-- Consolidated seed data for POC and development environment
-- All data is for development/demo purposes only

-- ===== SURVEYOR SEED DATA =====
-- Seed surveyors using real Keycloak user IDs from eclaims-realm.json
INSERT INTO workflow.surveyors (id, name, email, phone, region, active)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'Alice Surveyor',   'surveyor1@eclaims.test', '+918604403487', 'EAST', TRUE),
    ('20000000-0000-0000-0000-000000000002', 'Steve Surveyor',   'surveyor2@eclaims.test', '+918604403487', 'WEST', TRUE)
ON CONFLICT DO NOTHING;

-- ===== ADJUSTOR SEED DATA =====
-- Seed adjustors using real Keycloak user IDs from eclaims-realm.json
INSERT INTO workflow.adjustors (id, name, email, region, active)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'Bob Adjustor',    'adjustor1@eclaims.test', 'EAST', TRUE),
    ('30000000-0000-0000-0000-000000000002', 'Betty Adjustor',  'adjustor2@eclaims.test', 'WEST', TRUE)
ON CONFLICT DO NOTHING;

-- ===== WORKSHOP SEED DATA =====
-- Seed test workshops for demo - Repair Workshops
INSERT INTO workshops.workshops (id, name, address, city, zip_code, phone, email, rating, active, provider_type)
VALUES
    ('b1c2d3e4-0000-0000-0000-000000000001', 'AutoFix Premium',       '123 Main St',      'New York',    '10001', '+1-212-555-0101', 'autofix@workshop.test',    4.8, TRUE, 'REPAIR_WORKSHOP'),
    ('b1c2d3e4-0000-0000-0000-000000000002', 'QuickRepair Center',    '456 Oak Ave',      'Los Angeles', '90001', '+1-310-555-0202', 'quickrepair@workshop.test', 4.5, TRUE, 'REPAIR_WORKSHOP'),
    ('b1c2d3e4-0000-0000-0000-000000000003', 'TechAuto Workshop',     '789 Pine Rd',      'Chicago',     '60601', '+1-312-555-0303', 'techauto@workshop.test',    4.2, TRUE, 'REPAIR_WORKSHOP'),
    ('b1c2d3e4-0000-0000-0000-000000000004', 'Elite Motors Service',  '321 Elm Blvd',     'Houston',     '77001', '+1-713-555-0404', 'elitemotors@workshop.test', 4.9, TRUE, 'REPAIR_WORKSHOP'),
    ('b1c2d3e4-0000-0000-0000-000000000005', 'FastLane Auto',         '654 Maple St',     'Phoenix',     '85001', '+1-602-555-0505', 'fastlane@workshop.test',    4.3, TRUE, 'REPAIR_WORKSHOP')
ON CONFLICT DO NOTHING;

-- Seed Authorized Service Stations
INSERT INTO workshops.workshops (id, name, address, city, zip_code, phone, email, rating, active, provider_type)
VALUES
    ('c2d3e4f5-0000-0000-0000-000000000011', 'AutoNation Service Center',  '100 Service Blvd',   'New York',     '10002', '+1-212-555-1001', 'service@autonation.test',  4.6, TRUE, 'AUTH_SERVICE_STATION'),
    ('c2d3e4f5-0000-0000-0000-000000000012', 'Firestone Complete Auto',    '200 Tire Ave',       'Los Angeles',  '90002', '+1-310-555-1002', 'firestone@service.test',   4.4, TRUE, 'AUTH_SERVICE_STATION'),
    ('c2d3e4f5-0000-0000-0000-000000000013', 'Jiffy Lube Authorized',      '300 Quick Ln',       'Chicago',      '60602', '+1-312-555-1003', 'jiffylube@service.test',   4.1, TRUE, 'AUTH_SERVICE_STATION'),
    ('c2d3e4f5-0000-0000-0000-000000000014', 'Pep Boys Service',           '400 Motor St',       'Houston',      '77002', '+1-713-555-1004', 'pepboys@service.test',     4.3, TRUE, 'AUTH_SERVICE_STATION'),
    ('c2d3e4f5-0000-0000-0000-000000000015', 'Midas Auto Service',         '500 Muffler Dr',     'Phoenix',      '85002', '+1-602-555-1005', 'midas@service.test',       4.2, TRUE, 'AUTH_SERVICE_STATION')
ON CONFLICT DO NOTHING;

-- Seed Car Rental Partners
INSERT INTO workshops.workshops (id, name, address, city, zip_code, phone, email, rating, active, provider_type)
VALUES
    ('d3e4f5a6-0000-0000-0000-000000000021', 'Enterprise Rent-A-Car',     '111 Enterprise Way',  'New York',     '10003', '+1-212-555-2001', 'enterprise@rental.test',  4.7, TRUE, 'CAR_RENTAL'),
    ('d3e4f5a6-0000-0000-0000-000000000022', 'Hertz Vehicle Rentals',     '222 Hertz Blvd',      'Los Angeles',  '90003', '+1-310-555-2002', 'hertz@rental.test',       4.5, TRUE, 'CAR_RENTAL'),
    ('d3e4f5a6-0000-0000-0000-000000000023', 'Avis Car Rental',           '333 Avis Ave',        'Chicago',      '60603', '+1-312-555-2003', 'avis@rental.test',        4.3, TRUE, 'CAR_RENTAL'),
    ('d3e4f5a6-0000-0000-0000-000000000024', 'Budget Rent a Car',         '444 Budget St',       'Houston',      '77003', '+1-713-555-2004', 'budget@rental.test',      4.2, TRUE, 'CAR_RENTAL'),
    ('d3e4f5a6-0000-0000-0000-000000000025', 'National Car Rental',       '555 National Dr',     'Phoenix',      '85003', '+1-602-555-2005', 'national@rental.test',    4.4, TRUE, 'CAR_RENTAL')
ON CONFLICT DO NOTHING;

-- Link workshops to Keycloak accounts
UPDATE workshops.workshops
SET keycloak_user_id = '60000000-0000-0000-0000-000000000001'
WHERE id = 'b1c2d3e4-0000-0000-0000-000000000001';

UPDATE workshops.workshops
SET keycloak_user_id = '60000000-0000-0000-0000-000000000002'
WHERE id = 'b1c2d3e4-0000-0000-0000-000000000002';

-- ===== REPORTING KPI SEED DATA =====
-- Seed initial KPI snapshot for demo
INSERT INTO reporting.claim_kpi_snapshots
    (region, total_claims, submitted_today, pending_assignment, fraud_flagged, generated_at)
VALUES
    ('global', 0, 0, 0, 0, NOW())
ON CONFLICT DO NOTHING;

-- Demo seed for regions with realistic data for dashboards
INSERT INTO reporting.regional_kpi_snapshots (
    region, total_claims, submitted_today, pending_assignment,
    under_survey, under_adjudication, approved_this_month,
    rejected_this_month, settled_this_month, total_settled_amount,
    avg_processing_time_hours, fraud_flagged, generated_at
)
VALUES
    -- EAST: largest hub, high volume, average processing time
    ('EAST',    523, 4, 14, 28, 39, 97,  9, 81, 1485200.00, 36.2, 6, NOW()),
    -- WEST: second hub, tech-heavy, fastest processing
    ('WEST',    418, 3, 11, 22, 31, 74,  7, 66, 1203800.00, 29.7, 4, NOW()),
    -- NORTH: smaller region, slower but thorough
    ('NORTH',   287, 2,  8, 15, 20, 51,  5, 44,  821500.00, 44.1, 3, NOW()),
    -- SOUTH: high fraud activity, slower processing
    ('SOUTH',   364, 2, 10, 19, 26, 63,  5, 58,  774320.00, 52.8, 8, NOW() - interval '2 minutes'),
    -- CENTRAL: newest region, ramping up
    ('CENTRAL', 255, 1,  0,  3,  8, 27,  2, 40,  500500.00, 41.3, 0, NOW() - interval '5 minutes')
ON CONFLICT DO NOTHING;

-- If no real claims exist yet, insert a realistic demo snapshot
INSERT INTO reporting.claim_kpi_snapshots (
    region, total_claims, submitted_today, pending_assignment,
    under_survey, under_adjudication, approved_this_month,
    rejected_this_month, settled_this_month, total_settled_amount,
    average_cycle_hours, fraud_flagged, generated_at
)
SELECT
    'global', 1847, 12, 43, 87, 124, 312, 28, 289,
    4785320.00, 38.4, 17, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM reporting.claim_kpi_snapshots WHERE region = 'global'
);

-- ===== TEST POLICY CACHE =====
-- Seed test policies for development
INSERT INTO claims.policy_cache (policy_number, customer_id, customer_email, coverage_type, policy_start_date, policy_end_date)
VALUES
    ('POL-2024-001', 'cust-001', 'john.doe@test.com', 'COMPREHENSIVE', '2024-01-01', '2024-12-31'),
    ('POL-2024-002', 'cust-002', 'jane.smith@test.com', 'LIABILITY', '2024-01-15', '2025-01-15'),
    ('POL-2024-003', 'cust-003', 'bob.johnson@test.com', 'COLLISION', '2024-02-01', '2025-02-01'),
    ('POL-2024-004', 'cust-004', 'alice.brown@test.com', 'COMPREHENSIVE', '2024-03-01', '2025-03-01'),
    ('POL-2024-005', 'cust-005', 'charlie.davis@test.com', 'LIABILITY', '2024-04-01', '2025-04-01')
ON CONFLICT DO NOTHING;

-- ===== DEMO CUSTOMER PROFILES =====
-- Seed some customer profiles for development
INSERT INTO customers.customer_profiles (customer_id, phone, address_line1, city, state, zip_code, country)
VALUES
    ('cust-001', '+1-555-0001', '123 Main St', 'New York', 'NY', '10001', 'US'),
    ('cust-002', '+1-555-0002', '456 Oak Ave', 'Los Angeles', 'CA', '90001', 'US'),
    ('cust-003', '+1-555-0003', '789 Pine Rd', 'Chicago', 'IL', '60601', 'US'),
    ('cust-004', '+1-555-0004', '321 Elm Blvd', 'Houston', 'TX', '77001', 'US'),
    ('cust-005', '+1-555-0005', '654 Maple St', 'Phoenix', 'AZ', '85001', 'US')
ON CONFLICT (customer_id) DO NOTHING;

-- Auto-assign regions to demo claims based on incident location
UPDATE claims.claims
SET region = CASE
    WHEN incident_location ILIKE '%new york%' OR incident_location ILIKE '%boston%'
         OR incident_location ILIKE '%philadelphia%' OR incident_location ILIKE '%EAST%'  THEN 'EAST'
    WHEN incident_location ILIKE '%los angeles%' OR incident_location ILIKE '%san francisco%'
         OR incident_location ILIKE '%seattle%' OR incident_location ILIKE '%WEST%'        THEN 'WEST'
    WHEN incident_location ILIKE '%chicago%' OR incident_location ILIKE '%detroit%'
         OR incident_location ILIKE '%minneapolis%' OR incident_location ILIKE '%NORTH%'   THEN 'NORTH'
    WHEN incident_location ILIKE '%houston%' OR incident_location ILIKE '%miami%'
         OR incident_location ILIKE '%atlanta%' OR incident_location ILIKE '%SOUTH%'       THEN 'SOUTH'
    ELSE 'CENTRAL'
END
WHERE region IS NULL;