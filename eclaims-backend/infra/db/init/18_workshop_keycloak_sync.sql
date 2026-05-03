-- Workshop - Keycloak User Sync
-- Links the two active partner workshop DB records to their corresponding Keycloak user accounts.
-- East: AutoFix Premium (New York)  -> Keycloak user autofix_east    (60000000-0000-0000-0000-000000000001)
-- West: QuickRepair Center (LA)     -> Keycloak user quickrepair_west (60000000-0000-0000-0000-000000000002)

ALTER TABLE workshops.workshops
    ADD COLUMN IF NOT EXISTS keycloak_user_id VARCHAR(36) UNIQUE;

CREATE INDEX IF NOT EXISTS idx_workshops_keycloak_user
    ON workshops.workshops(keycloak_user_id);

-- Bind AutoFix Premium (New York/East) to Keycloak account autofix_east
UPDATE workshops.workshops
SET keycloak_user_id = '60000000-0000-0000-0000-000000000001'
WHERE id = 'b1c2d3e4-0000-0000-0000-000000000001';

-- Bind QuickRepair Center (Los Angeles/West) to Keycloak account quickrepair_west
UPDATE workshops.workshops
SET keycloak_user_id = '60000000-0000-0000-0000-000000000002'
WHERE id = 'b1c2d3e4-0000-0000-0000-000000000002';

COMMENT ON COLUMN workshops.workshops.keycloak_user_id IS 'Keycloak subject UUID for the partner workshop login account - enables workshop portal self-identification';
