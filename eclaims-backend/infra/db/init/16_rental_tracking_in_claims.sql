-- Migration: Add rental vehicle tracking to claims table
-- Author: System
-- Date: 2026-05-01
-- Description: Track whether customer reserved or skipped rental vehicle

-- Add rental status enum
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'rental_status') THEN
        CREATE TYPE claims.rental_status AS ENUM ('NOT_SELECTED', 'RESERVED', 'SKIPPED');
    END IF;
END $$;

-- Add rental tracking columns to claims table
ALTER TABLE claims.claims
ADD COLUMN IF NOT EXISTS rental_reservation_id UUID,
ADD COLUMN IF NOT EXISTS rental_status VARCHAR(20) DEFAULT 'NOT_SELECTED';

-- Add index for rental reservation lookups
CREATE INDEX IF NOT EXISTS idx_claims_rental_reservation
ON claims.claims(rental_reservation_id)
WHERE rental_reservation_id IS NOT NULL;

-- Note: Foreign key constraint to rentals.rental_reservations not added yet
-- because RentalController currently returns mock data without persisting.
-- TODO: Add FK constraint once rental persistence is implemented:
-- ALTER TABLE claims.claims
-- ADD CONSTRAINT fk_claims_rental_reservation
-- FOREIGN KEY (rental_reservation_id) REFERENCES rentals.rental_reservations(id) ON DELETE SET NULL;

-- Add comment
COMMENT ON COLUMN claims.claims.rental_reservation_id IS 'UUID of the rental reservation if customer reserved a vehicle';
COMMENT ON COLUMN claims.claims.rental_status IS 'Tracks rental vehicle decision: NOT_SELECTED, RESERVED, SKIPPED';
