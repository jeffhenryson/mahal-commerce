-- Índice parcial em users.enabled para queries filtradas por status (ex: GET /users?enabled=false).
-- Filtro restrito a registros ativos (deleted_at IS NULL) para manter o índice pequeno.
CREATE INDEX idx_users_enabled ON users (enabled) WHERE deleted_at IS NULL;
