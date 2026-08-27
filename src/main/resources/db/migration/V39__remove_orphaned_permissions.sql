-- Remove permissões que existem no banco mas não são verificadas por nenhum @PreAuthorize.
-- ROLE_CREATE, ROLE_DELETE, PERMISSION_CREATE e PERMISSION_DELETE foram inseridas pela V36
-- como "dados de referência", mas os endpoints usam DEV_ROLE_MANAGE e DEV_PERMISSION_MANAGE.
-- Mantê-las visíveis via GET /permissions criava falsa expectativa de que teriam efeito.
-- V37 já removeu os grants de ROLE_DEV; esta migration remove as permissões em si.

DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT id FROM permissions
    WHERE name IN ('ROLE_CREATE', 'ROLE_DELETE', 'PERMISSION_CREATE', 'PERMISSION_DELETE')
);

DELETE FROM permissions
WHERE name IN ('ROLE_CREATE', 'ROLE_DELETE', 'PERMISSION_CREATE', 'PERMISSION_DELETE');
