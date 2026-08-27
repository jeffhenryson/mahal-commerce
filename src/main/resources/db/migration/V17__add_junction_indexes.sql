-- Reverse-lookup indexes for junction tables.
-- The composite PKs cover queries filtered by the *first* column (role_id, user_id),
-- but not queries filtered by the second column alone (e.g. "which roles have permission X?",
-- "which users carry role Y?"). These indexes fill that gap.

CREATE INDEX IF NOT EXISTS idx_rp_permission_id ON role_permissions (permission_id);
CREATE INDEX IF NOT EXISTS idx_ur_role_id        ON user_roles       (role_id);
