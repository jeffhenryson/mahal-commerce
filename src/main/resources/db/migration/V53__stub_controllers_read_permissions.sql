INSERT INTO permissions (name) VALUES
    ('COMPRAS_READ'),
    ('ECOMMERCE_READ'),
    ('FINANCEIRO_READ'),
    ('LOGISTICA_READ'),
    ('PDV_READ');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
  AND p.name IN ('COMPRAS_READ', 'ECOMMERCE_READ', 'FINANCEIRO_READ', 'LOGISTICA_READ', 'PDV_READ');
