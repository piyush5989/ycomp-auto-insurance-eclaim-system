-- Workshops Enhancement Migration
-- Adds provider_type to distinguish Repair Workshops, Authorized Service Stations, and Car Rentals.
-- Satisfies FR8: Customer should be able to check Partner Service providers by location/zip.

ALTER TABLE workshops.workshops
    ADD COLUMN IF NOT EXISTS provider_type VARCHAR(30) NOT NULL DEFAULT 'REPAIR_WORKSHOP';

-- Update existing seeded repair workshops to have explicit provider_type
UPDATE workshops.workshops SET provider_type = 'REPAIR_WORKSHOP' WHERE provider_type = 'REPAIR_WORKSHOP';

-- Seed: Authorized Service Stations
INSERT INTO workshops.workshops (id, name, address, city, zip_code, phone, email, rating, active, provider_type)
VALUES
    ('c2d3e4f5-0000-0000-0000-000000000011', 'AutoNation Service Center',  '100 Service Blvd',   'New York',     '10002', '+1-212-555-1001', 'service@autonation.test',  4.6, TRUE, 'AUTH_SERVICE_STATION'),
    ('c2d3e4f5-0000-0000-0000-000000000012', 'Firestone Complete Auto',    '200 Tire Ave',       'Los Angeles',  '90002', '+1-310-555-1002', 'firestone@service.test',   4.4, TRUE, 'AUTH_SERVICE_STATION'),
    ('c2d3e4f5-0000-0000-0000-000000000013', 'Jiffy Lube Authorized',      '300 Quick Ln',       'Chicago',      '60602', '+1-312-555-1003', 'jiffylube@service.test',   4.1, TRUE, 'AUTH_SERVICE_STATION'),
    ('c2d3e4f5-0000-0000-0000-000000000014', 'Pep Boys Service',           '400 Motor St',       'Houston',      '77002', '+1-713-555-1004', 'pepboys@service.test',     4.3, TRUE, 'AUTH_SERVICE_STATION'),
    ('c2d3e4f5-0000-0000-0000-000000000015', 'Midas Auto Service',         '500 Muffler Dr',     'Phoenix',      '85002', '+1-602-555-1005', 'midas@service.test',       4.2, TRUE, 'AUTH_SERVICE_STATION')
ON CONFLICT DO NOTHING;

-- Seed: Car Rental Partners
INSERT INTO workshops.workshops (id, name, address, city, zip_code, phone, email, rating, active, provider_type)
VALUES
    ('d3e4f5a6-0000-0000-0000-000000000021', 'Enterprise Rent-A-Car',     '111 Enterprise Way',  'New York',     '10003', '+1-212-555-2001', 'enterprise@rental.test',  4.7, TRUE, 'CAR_RENTAL'),
    ('d3e4f5a6-0000-0000-0000-000000000022', 'Hertz Vehicle Rentals',     '222 Hertz Blvd',      'Los Angeles',  '90003', '+1-310-555-2002', 'hertz@rental.test',       4.5, TRUE, 'CAR_RENTAL'),
    ('d3e4f5a6-0000-0000-0000-000000000023', 'Avis Car Rental',           '333 Avis Ave',        'Chicago',      '60603', '+1-312-555-2003', 'avis@rental.test',        4.3, TRUE, 'CAR_RENTAL'),
    ('d3e4f5a6-0000-0000-0000-000000000024', 'Budget Rent a Car',         '444 Budget St',       'Houston',      '77003', '+1-713-555-2004', 'budget@rental.test',      4.2, TRUE, 'CAR_RENTAL'),
    ('d3e4f5a6-0000-0000-0000-000000000025', 'National Car Rental',       '555 National Dr',     'Phoenix',      '85003', '+1-602-555-2005', 'national@rental.test',    4.4, TRUE, 'CAR_RENTAL')
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_workshops_provider_type
    ON workshops.workshops(provider_type, active);

CREATE INDEX IF NOT EXISTS idx_workshops_zip_provider
    ON workshops.workshops(zip_code, provider_type, active);
