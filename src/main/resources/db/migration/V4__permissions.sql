-- Permissões granulares para RBAC (Role -> Permission)

CREATE TABLE IF NOT EXISTS permissions (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    CONSTRAINT uk_permission_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles(id)       ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id)
);

-- Permissões base
INSERT INTO permissions (name) VALUES
    ('USER_CREATE'),
    ('USER_READ'),
    ('USER_UPDATE'),
    ('USER_DELETE'),
    ('USER_ROLE_ASSIGN')
ON CONFLICT (name) DO NOTHING;

-- ROLE_ADMIN recebe todas as permissões
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- ROLE_USER recebe apenas leitura
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_USER'
  AND p.name = 'USER_READ'
ON CONFLICT DO NOTHING;
