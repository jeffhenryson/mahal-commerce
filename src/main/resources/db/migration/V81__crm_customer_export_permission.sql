INSERT INTO permissions (name) VALUES ('CRM_CUSTOMER_EXPORT')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'CRM_CUSTOMER_EXPORT'
ON CONFLICT DO NOTHING;
