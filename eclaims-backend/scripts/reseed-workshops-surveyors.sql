-- Idempotent reference data for local/demo after TRUNCATE or empty DB.
-- Repair workshops (FR8 / workshop search), partner types from 11_workshops_enhancements,
-- surveyors for auto-assignment (04_workflow_schema).

INSERT INTO workshops.workshops (
    id, name, address, city, zip_code, phone, email, rating, active, provider_type, keycloak_user_id
) VALUES
    ('b1c2d3e4-0000-0000-0000-000000000001', 'AutoFix Premium',       '123 Main St',      'New York',    '10001', '+918604403487', 'autofix@workshop.test',     4.8, TRUE, 'REPAIR_WORKSHOP', '60000000-0000-0000-0000-000000000001'),
    ('b1c2d3e4-0000-0000-0000-000000000002', 'QuickRepair Center',   '456 Oak Ave',      'Los Angeles', '90001', '+918604403487', 'quickrepair@workshop.test', 4.5, TRUE, 'REPAIR_WORKSHOP', '60000000-0000-0000-0000-000000000002'),
    ('b1c2d3e4-0000-0000-0000-000000000003', 'TechAuto Workshop',    '789 Pine Rd',      'Chicago',     '60601', '+918604403487', 'techauto@workshop.test',    4.2, TRUE, 'REPAIR_WORKSHOP', NULL),
    ('b1c2d3e4-0000-0000-0000-000000000004', 'Elite Motors Service', '321 Elm Blvd',     'Houston',     '77001', '+918604403487', 'elitemotors@workshop.test', 4.9, TRUE, 'REPAIR_WORKSHOP', NULL),
    ('b1c2d3e4-0000-0000-0000-000000000005', 'FastLane Auto',        '654 Maple St',     'Phoenix',     '85001', '+918604403487', 'fastlane@workshop.test',    4.3, TRUE, 'REPAIR_WORKSHOP', NULL),
    ('c2d3e4f5-0000-0000-0000-000000000011', 'AutoNation Service Center',  '100 Service Blvd',   'New York',    '10002', '+918604403487', 'service@autonation.test',  4.6, TRUE, 'AUTH_SERVICE_STATION', NULL),
    ('c2d3e4f5-0000-0000-0000-000000000012', 'Firestone Complete Auto',    '200 Tire Ave',       'Los Angeles',  '90002', '+918604403487', 'firestone@service.test',   4.4, TRUE, 'AUTH_SERVICE_STATION', NULL),
    ('c2d3e4f5-0000-0000-0000-000000000013', 'Jiffy Lube Authorized',      '300 Quick Ln',       'Chicago',      '60602', '+918604403487', 'jiffylube@service.test',   4.1, TRUE, 'AUTH_SERVICE_STATION', NULL),
    ('c2d3e4f5-0000-0000-0000-000000000014', 'Pep Boys Service',           '400 Motor St',       'Houston',      '77002', '+918604403487', 'pepboys@service.test',     4.3, TRUE, 'AUTH_SERVICE_STATION', NULL),
    ('c2d3e4f5-0000-0000-0000-000000000015', 'Midas Auto Service',         '500 Muffler Dr',     'Phoenix',      '85002', '+918604403487', 'midas@service.test',       4.2, TRUE, 'AUTH_SERVICE_STATION', NULL),
    ('d3e4f5a6-0000-0000-0000-000000000021', 'Enterprise Rent-A-Car',     '111 Enterprise Way',  'New York',     '10003', '+918604403487', 'enterprise@rental.test',  4.7, TRUE, 'CAR_RENTAL', NULL),
    ('d3e4f5a6-0000-0000-0000-000000000022', 'Hertz Vehicle Rentals',     '222 Hertz Blvd',      'Los Angeles',  '90003', '+918604403487', 'hertz@rental.test',       4.5, TRUE, 'CAR_RENTAL', NULL),
    ('d3e4f5a6-0000-0000-0000-000000000023', 'Avis Car Rental',           '333 Avis Ave',        'Chicago',      '60603', '+918604403487', 'avis@rental.test',        4.3, TRUE, 'CAR_RENTAL', NULL),
    ('d3e4f5a6-0000-0000-0000-000000000024', 'Budget Rent a Car',         '444 Budget St',       'Houston',      '77003', '+918604403487', 'budget@rental.test',      4.2, TRUE, 'CAR_RENTAL', NULL),
    ('d3e4f5a6-0000-0000-0000-000000000025', 'National Car Rental',       '555 National Dr',     'Phoenix',      '85003', '+918604403487', 'national@rental.test',    4.4, TRUE, 'CAR_RENTAL', NULL)
ON CONFLICT (id) DO NOTHING;

UPDATE workshops.workshops
SET phone = '+918604403487';

ALTER TABLE workflow.surveyors
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20);

ALTER TABLE workflow.adjustors
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20);

ALTER TABLE customers.customer_profiles
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20);

INSERT INTO customers.customer_profiles (customer_id, phone)
VALUES
    ('10000000-0000-0000-0000-000000000001', '+918604403487'),
    ('10000000-0000-0000-0000-000000000002', '+918604403487')
ON CONFLICT (customer_id) DO NOTHING;

UPDATE customers.customer_profiles
SET phone = '+918604403487'
WHERE phone IS NULL OR phone = '';

INSERT INTO workflow.surveyors (id, name, email, phone, region, active)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'Alice Surveyor', 'surveyor1@eclaims.test', '+918604403487', 'EAST', TRUE),
    ('20000000-0000-0000-0000-000000000002', 'Steve Surveyor', 'surveyor2@eclaims.test', '+918604403487', 'WEST', TRUE)
ON CONFLICT (id) DO NOTHING;

UPDATE workflow.surveyors
SET phone = '+918604403487'
WHERE phone IS NULL OR phone = '';

INSERT INTO workflow.adjustors (id, name, email, phone, region, active, field_office, service_areas)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'Bob Adjustor',   'adjustor1@eclaims.test', '+918604403487', 'EAST', TRUE, 'Boston Office', '["EAST", "NORTHEAST"]'),
    ('30000000-0000-0000-0000-000000000002', 'Betty Adjustor', 'adjustor2@eclaims.test', '+918604403487', 'WEST', TRUE, 'San Francisco Office', '["WEST", "SOUTHWEST"]')
ON CONFLICT (id) DO NOTHING;

UPDATE workflow.adjustors
SET phone = '+918604403487'
WHERE phone IS NULL OR phone = '';
