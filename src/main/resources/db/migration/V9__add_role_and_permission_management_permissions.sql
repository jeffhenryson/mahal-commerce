-- Permissions para gestão de roles e permissions via API admin
INSERT INTO permissions (name) VALUES
    ('ROLE_READ'),
    ('ROLE_CREATE'),
    ('ROLE_DELETE'),
    ('ROLE_MANAGE_PERMISSIONS'),
    ('PERMISSION_READ'),
    ('PERMISSION_CREATE'),
    ('PERMISSION_DELETE')
ON CONFLICT (name) DO NOTHING;

-- Atribui todas as novas permissions ao ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
  AND p.name IN (
      'ROLE_READ', 'ROLE_CREATE', 'ROLE_DELETE', 'ROLE_MANAGE_PERMISSIONS',
      'PERMISSION_READ', 'PERMISSION_CREATE', 'PERMISSION_DELETE'
  )
ON CONFLICT DO NOTHING;
