-- Migration: Surveyor ZIP coverage table
-- Replaces the hardcoded coversZipCode() / coversZip3() logic in AutoAssignmentService.
-- Each row maps a ZIP prefix (3 or 5 digits) to a surveyor region so the service can
-- look up coverage dynamically instead of using hardcoded conditionals.

CREATE TABLE IF NOT EXISTS workflow.surveyor_zip_coverage (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    region     VARCHAR(50) NOT NULL,
    zip_prefix VARCHAR(5)  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uidx_coverage_zip ON workflow.surveyor_zip_coverage (zip_prefix);
CREATE INDEX IF NOT EXISTS idx_coverage_region ON workflow.surveyor_zip_coverage (region);

-- ── Seed data ───────────────────────────────────────────────────────────────
-- EAST — Boston / New England area (021xx–028xx)
INSERT INTO workflow.surveyor_zip_coverage (region, zip_prefix) VALUES
    ('EAST', '021'),  -- Boston core
    ('EAST', '022'),
    ('EAST', '023'),
    ('EAST', '024'),
    ('EAST', '025'),
    ('EAST', '026'),
    ('EAST', '027'),
    ('EAST', '028'),
    ('EAST', '029')
ON CONFLICT (zip_prefix) DO NOTHING;

-- WEST — San Francisco Bay Area / Los Angeles (900xx, 901xx, 941xx–949xx)
INSERT INTO workflow.surveyor_zip_coverage (region, zip_prefix) VALUES
    ('WEST', '900'),
    ('WEST', '901'),
    ('WEST', '902'),
    ('WEST', '941'),  -- San Francisco core
    ('WEST', '942'),
    ('WEST', '943'),
    ('WEST', '944'),
    ('WEST', '945'),
    ('WEST', '946'),
    ('WEST', '947'),
    ('WEST', '948'),
    ('WEST', '949')
ON CONFLICT (zip_prefix) DO NOTHING;

-- CENTRAL — Chicago / Midwest (606xx–607xx)
INSERT INTO workflow.surveyor_zip_coverage (region, zip_prefix) VALUES
    ('CENTRAL', '606'),
    ('CENTRAL', '607'),
    ('CENTRAL', '608'),
    ('CENTRAL', '550'),  -- Minneapolis
    ('CENTRAL', '551'),
    ('CENTRAL', '441'),  -- Cleveland
    ('CENTRAL', '442')
ON CONFLICT (zip_prefix) DO NOTHING;

-- SOUTH — Houston / Dallas / Miami (770xx, 750xx, 331xx)
INSERT INTO workflow.surveyor_zip_coverage (region, zip_prefix) VALUES
    ('SOUTH', '770'),  -- Houston core
    ('SOUTH', '771'),
    ('SOUTH', '750'),  -- Dallas
    ('SOUTH', '751'),
    ('SOUTH', '331'),  -- Miami
    ('SOUTH', '332'),
    ('SOUTH', '303'),  -- Atlanta
    ('SOUTH', '304')
ON CONFLICT (zip_prefix) DO NOTHING;
