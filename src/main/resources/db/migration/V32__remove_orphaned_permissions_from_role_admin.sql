-- ROLE_CREATE, ROLE_DELETE, PERMISSION_CREATE e PERMISSION_DELETE foram atribuídas ao
-- ROLE_ADMIN em V9, mas nenhum endpoint as verifica — os controllers usam DEV_ROLE_MANAGE
-- e DEV_PERMISSION_MANAGE (exclusivas de ROLE_DEV). Manter essas permissions em ROLE_ADMIN
-- cria uma falsa expectativa: o admin as vê no banco mas recebe 403 ao tentar usá-las.
DELETE FROM role_permissions
WHERE role_id  = (SELECT id FROM roles       WHERE name = 'ROLE_ADMIN')
  AND permission_id IN (
      SELECT id FROM permissions
      WHERE name IN ('ROLE_CREATE', 'ROLE_DELETE', 'PERMISSION_CREATE', 'PERMISSION_DELETE')
  );
