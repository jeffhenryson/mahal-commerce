-- Garante que as permissões exclusivas de ROLE_DEV existam como dados de referência.
-- DevRoleBootstrapConfig ainda as cria via código (idempotente), mas esta migration
-- torna o schema explícito e permite auditoria via histórico de migrations.
INSERT INTO permissions (name) VALUES
    ('ROLE_CREATE'),
    ('ROLE_DELETE'),
    ('PERMISSION_CREATE'),
    ('PERMISSION_DELETE'),
    ('DEV_ROLE_MANAGE'),
    ('DEV_PERMISSION_MANAGE')
ON CONFLICT (name) DO NOTHING;
