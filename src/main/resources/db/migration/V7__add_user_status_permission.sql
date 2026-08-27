-- Adiciona a permissão USER_STATUS para controle de enable/disable de contas.
-- Separa semanticamente "desativar conta" de "deletar conta" (USER_DELETE).
INSERT INTO permissions (name) VALUES ('USER_STATUS') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
  AND p.name = 'USER_STATUS'
ON CONFLICT DO NOTHING;
