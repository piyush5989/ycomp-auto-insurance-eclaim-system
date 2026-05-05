-- Wipe all eClaims application data (bounded-context schemas only).
-- Does NOT touch Keycloak / public schema — users and realm config stay intact.
--
-- After this, you can submit a new claim with a clean slate. Workshop + surveyor
-- reference rows are re-inserted below so partner search still works.

DO $$
DECLARE
  stmt text;
BEGIN
  SELECT 'TRUNCATE TABLE '
      || string_agg(
           format('%I.%I', schemaname, tablename),
           ', '
           ORDER BY schemaname, tablename
         )
      || ' RESTART IDENTITY CASCADE'
  INTO stmt
  FROM pg_tables
  WHERE schemaname IN (
      'claims',
      'documents',
      'workflow',
      'workshops',
      'payments',
      'reporting',
      'audit',
      'customers',
      'notifications',
      'rentals'
    );

  IF stmt IS NOT NULL AND stmt <> 'TRUNCATE TABLE  RESTART IDENTITY CASCADE' THEN
    RAISE NOTICE '%', stmt;
    EXECUTE stmt;
    RAISE NOTICE 'eClaims application tables truncated.';
  ELSE
    RAISE NOTICE 'No application tables found — nothing to truncate.';
  END IF;
END $$;

-- Restore partner workshops + surveyors (same seeds as infra/db/init — safe after TRUNCATE)
INSERT INTO workshops.workshops (
    id, name, address, city, zip_code, phone, email, rating, active, provider_type, keycloak_user_id
) VALUES
    ('b1c2d3e4-0000-0000-0000-000000000001', 'AutoFix Premium',       '123 Main St',      'New York',    '10001', '+1-212-555-0101', 'autofix@workshop.test',     4.8, TRUE, 'REPAIR_WORKSHOP', '60000000-0000-0000-0000-000000000001'),
    ('b1c2d3e4-0000-0000-0000-000000000002', 'QuickRepair Center',   '456 Oak Ave',      'Los Angeles', '90001', '+1-310-555-0202', 'quickrepair@workshop.test', 4.5, TRUE, 'REPAIR_WORKSHOP', '60000000-0000-0000-0000-000000000002'),
    ('b1c2d3e4-0000-0000-0000-000000000003', 'TechAuto Workshop',    '789 Pine Rd',      'Chicago',     '60601', '+1-312-555-0303', 'techauto@workshop.test',    4.2, TRUE, 'REPAIR_WORKSHOP', NULL),
    ('b1c2d3e4-0000-0000-0000-000000000004', 'Elite Motors Service', '321 Elm Blvd',     'Houston',     '77001', '+1-713-555-0404', 'elitemotors@workshop.test', 4.9, TRUE, 'REPAIR_WORKSHOP', NULL),
    ('b1c2d3e4-0000-0000-0000-000000000005', 'FastLane Auto',        '654 Maple St',     'Phoenix',     '85001', '+1-602-555-0505', 'fastlane@workshop.test',    4.3, TRUE, 'REPAIR_WORKSHOP', NULL),
    ('c2d3e4f5-0000-0000-0000-000000000011', 'AutoNation Service Center',  '100 Service Blvd',   'New York',    '10002', '+1-212-555-1001', 'service@autonation.test',  4.6, TRUE, 'AUTH_SERVICE_STATION', NULL),
    ('c2d3e4f5-0000-0000-0000-000000000012', 'Firestone Complete Auto',    '200 Tire Ave',       'Los Angeles',  '90002', '+1-310-555-1002', 'firestone@service.test',   4.4, TRUE, 'AUTH_SERVICE_STATION', NULL),
    ('c2d3e4f5-0000-0000-0000-000000000013', 'Jiffy Lube Authorized',      '300 Quick Ln',       'Chicago',      '60602', '+1-312-555-1003', 'jiffylube@service.test',   4.1, TRUE, 'AUTH_SERVICE_STATION', NULL),
    ('c2d3e4f5-0000-0000-0000-000000000014', 'Pep Boys Service',           '400 Motor St',       'Houston',      '77002', '+1-713-555-1004', 'pepboys@service.test',     4.3, TRUE, 'AUTH_SERVICE_STATION', NULL),
    ('c2d3e4f5-0000-0000-0000-000000000015', 'Midas Auto Service',         '500 Muffler Dr',     'Phoenix',      '85002', '+1-602-555-1005', 'midas@service.test',       4.2, TRUE, 'AUTH_SERVICE_STATION', NULL),
    ('d3e4f5a6-0000-0000-0000-000000000021', 'Enterprise Rent-A-Car',     '111 Enterprise Way',  'New York',     '10003', '+1-212-555-2001', 'enterprise@rental.test',  4.7, TRUE, 'CAR_RENTAL', NULL),
    ('d3e4f5a6-0000-0000-0000-000000000022', 'Hertz Vehicle Rentals',     '222 Hertz Blvd',      'Los Angeles',  '90003', '+1-310-555-2002', 'hertz@rental.test',       4.5, TRUE, 'CAR_RENTAL', NULL),
    ('d3e4f5a6-0000-0000-0000-000000000023', 'Avis Car Rental',           '333 Avis Ave',        'Chicago',      '60603', '+1-312-555-2003', 'avis@rental.test',        4.3, TRUE, 'CAR_RENTAL', NULL),
    ('d3e4f5a6-0000-0000-0000-000000000024', 'Budget Rent a Car',         '444 Budget St',       'Houston',      '77003', '+1-713-555-2004', 'budget@rental.test',      4.2, TRUE, 'CAR_RENTAL', NULL),
    ('d3e4f5a6-0000-0000-0000-000000000025', 'National Car Rental',       '555 National Dr',     'Phoenix',      '85003', '+1-602-555-2005', 'national@rental.test',    4.4, TRUE, 'CAR_RENTAL', NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO workflow.surveyors (id, name, email, region, active)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'Alice Surveyor', 'surveyor1@eclaims.test', 'EAST', TRUE),
    ('20000000-0000-0000-0000-000000000002', 'Steve Surveyor', 'surveyor2@eclaims.test', 'WEST', TRUE)
ON CONFLICT (id) DO NOTHING;
