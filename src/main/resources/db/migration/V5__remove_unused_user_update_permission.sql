-- Remove permissão USER_UPDATE: sem endpoint correspondente no sistema
DELETE FROM role_permissions
WHERE permission_id = (SELECT id FROM permissions WHERE name = 'USER_UPDATE');

DELETE FROM permissions WHERE name = 'USER_UPDATE';
