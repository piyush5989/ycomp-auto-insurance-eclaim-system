-- Workshop and Rental Vehicle Enhancements
-- Schema for workshop selection, vehicle drop-off, and rental vehicle features

-- ══════════════════════════════════════════════════════════════════════════════
-- WORKSHOPS SCHEMA ENHANCEMENTS
-- ══════════════════════════════════════════════════════════════════════════════

-- Add location and territory information to workshops
ALTER TABLE workshops.workshops
ADD COLUMN IF NOT EXISTS latitude DECIMAL(10, 8),
ADD COLUMN IF NOT EXISTS longitude DECIMAL(11, 8),
ADD COLUMN IF NOT EXISTS zip_code VARCHAR(10),
ADD COLUMN IF NOT EXISTS state VARCHAR(2),
ADD COLUMN IF NOT EXISTS territory_code VARCHAR(20),
ADD COLUMN IF NOT EXISTS is_partner BOOLEAN DEFAULT TRUE;

-- Update existing workshops with location data
UPDATE workshops.workshops 
SET latitude = 42.3601, longitude = -71.0589, zip_code = '02101', state = 'MA', territory_code = 'EAST-MA', is_partner = TRUE
WHERE id = 'w1w1w1w1-0000-0000-0000-000000000001';

UPDATE workshops.workshops 
SET latitude = 37.7749, longitude = -122.4194, zip_code = '94102', state = 'CA', territory_code = 'WEST-CA', is_partner = TRUE
WHERE id = 'w2w2w2w2-0000-0000-0000-000000000002';

-- Vehicle drop-off tracking
CREATE TABLE IF NOT EXISTS workshops.vehicle_dropoffs (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id            UUID        NOT NULL,
    workshop_id         UUID        NOT NULL REFERENCES workshops.workshops(id),
    dropped_off_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expected_pickup_at  TIMESTAMPTZ,
    actual_pickup_at    TIMESTAMPTZ,
    drop_off_notes      TEXT,
    mileage             INTEGER,
    fuel_level          VARCHAR(20),
    vehicle_condition   TEXT,
    photos_uploaded     BOOLEAN DEFAULT FALSE,
    confirmed_by        VARCHAR(100),
    status              VARCHAR(30) DEFAULT 'DROPPED_OFF',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vehicle_dropoffs_claim
    ON workshops.vehicle_dropoffs(claim_id);

CREATE INDEX IF NOT EXISTS idx_vehicle_dropoffs_workshop
    ON workshops.vehicle_dropoffs(workshop_id, status);

-- ══════════════════════════════════════════════════════════════════════════════
-- RENTAL VEHICLES SCHEMA
-- ══════════════════════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS rentals;

-- Rental vehicle inventory
CREATE TABLE IF NOT EXISTS rentals.rental_vehicles (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id         UUID        NOT NULL,
    vehicle_type        VARCHAR(50) NOT NULL,
    make                VARCHAR(50) NOT NULL,
    model               VARCHAR(50) NOT NULL,
    year                INTEGER,
    category            VARCHAR(30) NOT NULL, -- ECONOMY, COMPACT, MIDSIZE, FULLSIZE, SUV, VAN
    daily_rate          DECIMAL(10,2) NOT NULL,
    availability_status VARCHAR(30) DEFAULT 'AVAILABLE',
    location_zip        VARCHAR(10),
    location_city       VARCHAR(100),
    location_state      VARCHAR(2),
    features            TEXT,
    active              BOOLEAN DEFAULT TRUE
);

-- Rental providers
CREATE TABLE IF NOT EXISTS rentals.rental_providers (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    contact_email       VARCHAR(255),
    contact_phone       VARCHAR(20),
    location_address    VARCHAR(500),
    zip_code            VARCHAR(10),
    city                VARCHAR(100),
    state               VARCHAR(2),
    latitude            DECIMAL(10, 8),
    longitude           DECIMAL(11, 8),
    is_partner          BOOLEAN DEFAULT TRUE,
    active              BOOLEAN DEFAULT TRUE
);

-- Rental reservations
CREATE TABLE IF NOT EXISTS rentals.rental_reservations (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id            UUID        NOT NULL UNIQUE,
    customer_id         VARCHAR(100) NOT NULL,
    vehicle_id          UUID        NOT NULL REFERENCES rentals.rental_vehicles(id),
    provider_id         UUID        NOT NULL REFERENCES rentals.rental_providers(id),
    reservation_start   TIMESTAMPTZ NOT NULL,
    reservation_end     TIMESTAMPTZ,
    actual_pickup_at    TIMESTAMPTZ,
    actual_return_at    TIMESTAMPTZ,
    status              VARCHAR(30) DEFAULT 'RESERVED',
    daily_rate          DECIMAL(10,2) NOT NULL,
    total_cost          DECIMAL(10,2),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rental_reservations_claim
    ON rentals.rental_reservations(claim_id);

CREATE INDEX IF NOT EXISTS idx_rental_reservations_customer
    ON rentals.rental_reservations(customer_id);

-- ══════════════════════════════════════════════════════════════════════════════
-- NOTIFICATIONS SCHEMA
-- ══════════════════════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS notifications;

-- Notification queue/history
CREATE TABLE IF NOT EXISTS notifications.notifications (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id        VARCHAR(100) NOT NULL,
    recipient_type      VARCHAR(30) NOT NULL, -- CUSTOMER, SURVEYOR, ADJUSTOR, WORKSHOP, CASE_MANAGER
    notification_type   VARCHAR(50) NOT NULL,
    channel             VARCHAR(20) NOT NULL, -- EMAIL, SMS, IN_APP
    subject             VARCHAR(200),
    message             TEXT NOT NULL,
    claim_id            UUID,
    entity_id           VARCHAR(100),
    status              VARCHAR(30) DEFAULT 'PENDING',
    sent_at             TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient
    ON notifications.notifications(recipient_id, recipient_type, status);

CREATE INDEX IF NOT EXISTS idx_notifications_claim
    ON notifications.notifications(claim_id);

CREATE INDEX IF NOT EXISTS idx_notifications_created
    ON notifications.notifications(created_at DESC);

-- ══════════════════════════════════════════════════════════════════════════════
-- SURVEYOR TERRITORY COVERAGE
-- ══════════════════════════════════════════════════════════════════════════════

-- Surveyor coverage by ZIP codes
CREATE TABLE IF NOT EXISTS workflow.surveyor_coverage (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    surveyor_id         UUID        NOT NULL REFERENCES workflow.surveyors(id),
    zip_code            VARCHAR(10) NOT NULL,
    territory_code      VARCHAR(20),
    priority            INTEGER DEFAULT 1,
    UNIQUE(surveyor_id, zip_code)
);

CREATE INDEX IF NOT EXISTS idx_surveyor_coverage_zip
    ON workflow.surveyor_coverage(zip_code);

CREATE INDEX IF NOT EXISTS idx_surveyor_coverage_surveyor
    ON workflow.surveyor_coverage(surveyor_id);

-- Seed surveyor coverage data
INSERT INTO workflow.surveyor_coverage (surveyor_id, zip_code, territory_code)
VALUES
    -- Alice covers Boston area
    ('a1b2c3d4-0000-0000-0000-000000000001', '02101', 'EAST-MA'),
    ('a1b2c3d4-0000-0000-0000-000000000001', '02102', 'EAST-MA'),
    ('a1b2c3d4-0000-0000-0000-000000000001', '02103', 'EAST-MA'),
    -- Bob covers San Francisco area
    ('a1b2c3d4-0000-0000-0000-000000000002', '94102', 'WEST-CA'),
    ('a1b2c3d4-0000-0000-0000-000000000002', '94103', 'WEST-CA'),
    ('a1b2c3d4-0000-0000-0000-000000000002', '94104', 'WEST-CA'),
    -- Carol covers Boston area (backup)
    ('a1b2c3d4-0000-0000-0000-000000000003', '02101', 'EAST-MA'),
    ('a1b2c3d4-0000-0000-0000-000000000003', '02102', 'EAST-MA')
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════════════════════
-- SEED RENTAL DATA
-- ══════════════════════════════════════════════════════════════════════════════

-- Seed rental providers
INSERT INTO rentals.rental_providers (id, name, contact_email, contact_phone, zip_code, city, state, latitude, longitude, is_partner)
VALUES
    ('r1r1r1r1-0000-0000-0000-000000000001', 'Enterprise Rent-A-Car Boston', 'boston@enterprise.com', '617-555-0100', '02101', 'Boston', 'MA', 42.3601, -71.0589, TRUE),
    ('r2r2r2r2-0000-0000-0000-000000000002', 'Hertz San Francisco', 'sf@hertz.com', '415-555-0200', '94102', 'San Francisco', 'CA', 37.7749, -122.4194, TRUE)
ON CONFLICT DO NOTHING;

-- Seed rental vehicles
INSERT INTO rentals.rental_vehicles (id, provider_id, vehicle_type, make, model, year, category, daily_rate, location_zip, location_city, location_state)
VALUES
    ('rv1-0001', 'r1r1r1r1-0000-0000-0000-000000000001', 'Sedan', 'Toyota', 'Camry', 2024, 'MIDSIZE', 45.00, '02101', 'Boston', 'MA'),
    ('rv1-0002', 'r1r1r1r1-0000-0000-0000-000000000001', 'SUV', 'Honda', 'CR-V', 2024, 'SUV', 65.00, '02101', 'Boston', 'MA'),
    ('rv2-0001', 'r2r2r2r2-0000-0000-0000-000000000002', 'Sedan', 'Honda', 'Accord', 2024, 'MIDSIZE', 50.00, '94102', 'San Francisco', 'CA'),
    ('rv2-0002', 'r2r2r2r2-0000-0000-0000-000000000002', 'SUV', 'Ford', 'Explorer', 2024, 'SUV', 70.00, '94102', 'San Francisco', 'CA')
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════════════════════
-- COMMENTS
-- ══════════════════════════════════════════════════════════════════════════════

COMMENT ON TABLE workshops.vehicle_dropoffs IS 'Tracks vehicle drop-off at repair workshops';
COMMENT ON TABLE rentals.rental_vehicles IS 'Available rental vehicles for customers during repairs';
COMMENT ON TABLE rentals.rental_reservations IS 'Customer rental vehicle reservations linked to claims';
COMMENT ON TABLE notifications.notifications IS 'Multi-channel notification queue and history';
COMMENT ON TABLE workflow.surveyor_coverage IS 'ZIP code coverage mapping for surveyor territories';
