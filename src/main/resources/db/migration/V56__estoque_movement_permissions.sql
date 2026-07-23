INSERT INTO permissions (name) VALUES ('ESTOQUE_STOCK_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'ESTOQUE_STOCK_MANAGE'
ON CONFLICT DO NOTHING;
