INSERT INTO permissions (name) VALUES ('ESTOQUE_WAREHOUSE_READ'), ('ESTOQUE_WAREHOUSE_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('ESTOQUE_WAREHOUSE_READ', 'ESTOQUE_WAREHOUSE_MANAGE');
