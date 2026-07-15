INSERT INTO permissions (name) VALUES ('ESTOQUE_PRODUCT_READ'), ('ESTOQUE_PRODUCT_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('ESTOQUE_PRODUCT_READ', 'ESTOQUE_PRODUCT_MANAGE');
