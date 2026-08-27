-- Histórico: USER_UPDATE foi criada em V4 junto com as permissões base.
-- Em V5 foi removida com a justificativa de "endpoint inexistente".
-- O endpoint PATCH /users/{id} foi implementado depois — esta migration re-adiciona
-- a permission e a atribui ao ROLE_ADMIN.

INSERT INTO permissions (name)
VALUES ('USER_UPDATE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'USER_UPDATE'
ON CONFLICT DO NOTHING;
