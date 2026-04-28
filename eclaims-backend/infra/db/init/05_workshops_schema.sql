-- Workshops Schema

CREATE TABLE IF NOT EXISTS workshops.workshops (
    id          UUID            PRIMARY KEY,
    name        VARCHAR(150)    NOT NULL,
    address     VARCHAR(300),
    city        VARCHAR(100),
    zip_code    VARCHAR(20),
    phone       VARCHAR(20),
    email       VARCHAR(255),
    rating      DECIMAL(3,2)    DEFAULT 0.0,
    active      BOOLEAN         NOT NULL DEFAULT TRUE
);

-- Seed test workshops for demo
INSERT INTO workshops.workshops (id, name, address, city, zip_code, phone, email, rating, active)
VALUES
    ('b1c2d3e4-0000-0000-0000-000000000001', 'AutoFix Premium',       '123 Main St',      'New York',    '10001', '+1-212-555-0101', 'autofix@workshop.test',    4.8, TRUE),
    ('b1c2d3e4-0000-0000-0000-000000000002', 'QuickRepair Center',    '456 Oak Ave',      'Los Angeles', '90001', '+1-310-555-0202', 'quickrepair@workshop.test', 4.5, TRUE),
    ('b1c2d3e4-0000-0000-0000-000000000003', 'TechAuto Workshop',     '789 Pine Rd',      'Chicago',     '60601', '+1-312-555-0303', 'techauto@workshop.test',    4.2, TRUE),
    ('b1c2d3e4-0000-0000-0000-000000000004', 'Elite Motors Service',  '321 Elm Blvd',     'Houston',     '77001', '+1-713-555-0404', 'elitemotors@workshop.test', 4.9, TRUE),
    ('b1c2d3e4-0000-0000-0000-000000000005', 'FastLane Auto',         '654 Maple St',     'Phoenix',     '85001', '+1-602-555-0505', 'fastlane@workshop.test',    4.3, TRUE)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS workshops.work_orders (
    id                          UUID            PRIMARY KEY,
    claim_id                    UUID            NOT NULL,
    workshop_id                 UUID            NOT NULL REFERENCES workshops.workshops(id),
    estimated_cost              DECIMAL(12,2),
    final_cost                  DECIMAL(12,2),
    repair_status               VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    estimated_completion_date   DATE,
    work_description            VARCHAR(2000),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_work_orders_claim_id
    ON workshops.work_orders(claim_id);
