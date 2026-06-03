-- Garante que ROLE_DEV exista no schema independente do bootstrap (R2).
-- ON CONFLICT DO NOTHING torna idempotente em ambientes onde o bootstrap já rodou.
INSERT INTO roles (name)
VALUES ('ROLE_DEV')
ON CONFLICT (name) DO NOTHING;

-- Remove ROLE_CREATE, ROLE_DELETE, PERMISSION_CREATE e PERMISSION_DELETE de ROLE_DEV (R1).
-- Essas permissões não são verificadas por nenhum @PreAuthorize — os endpoints reais
-- usam DEV_ROLE_MANAGE e DEV_PERMISSION_MANAGE. Manter o grant criava falsa expectativa
-- de que essas permissões teriam efeito na autorização.
DELETE FROM role_permissions
WHERE role_id  = (SELECT id FROM roles       WHERE name = 'ROLE_DEV')
  AND permission_id IN (
      SELECT id FROM permissions
      WHERE name IN ('ROLE_CREATE', 'ROLE_DELETE', 'PERMISSION_CREATE', 'PERMISSION_DELETE')
  );
