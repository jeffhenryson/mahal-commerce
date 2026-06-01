-- =============================================================================
-- create-first-admin.sql
-- Cria usuário admin (ROLE_ADMIN) e usuário dev (ROLE_DEV) com todas as
-- permissões necessárias. Idempotente: seguro de rodar mais de uma vez
-- (ON CONFLICT DO NOTHING).
-- =============================================================================
--
-- ANTES DE EXECUTAR: gere o hash bcrypt da sua senha com UMA das opções abaixo
-- e substitua o placeholder na seção de usuários:
--
--   Python (requer: pip install bcrypt):
--     python3 -c "import bcrypt; print(bcrypt.hashpw(b'SUA_SENHA', bcrypt.gensalt(10)).decode())"
--
--   htpasswd (pacote apache2-utils / httpd-tools):
--     htpasswd -bnBC 10 "" SUA_SENHA | tr -d ':\n'
--
--   Docker (sem instalar nada):
--     docker run --rm python:3-alpine sh -c \
--       "pip -q install bcrypt && python3 -c \"import bcrypt; print(bcrypt.hashpw(b'SUA_SENHA', bcrypt.gensalt(10)).decode())\""
--
-- =============================================================================
-- COMO EXECUTAR (escolha uma das opções):
--
--   1. psql direto (postgres exposto na porta 5433 pelo docker-compose.yml):
--        PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres -d security \
--          -f scripts/create-first-admin.sql
--
--   2. Dentro do container:
--        docker exec -i postgres-hml psql -U postgres -d security \
--          < scripts/create-first-admin.sql
--
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Roles
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_USER'),
    ('ROLE_DEV')
ON CONFLICT (name) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Permissões — ADMIN + DEV exclusivas
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO permissions (name) VALUES
    -- Permissões de produto (ROLE_ADMIN tem estas)
    ('USER_CREATE'),
    ('USER_READ'),
    ('USER_UPDATE'),
    ('USER_DELETE'),
    ('USER_ROLE_ASSIGN'),
    ('USER_STATUS'),
    ('ROLE_READ'),
    ('ROLE_MANAGE_PERMISSIONS'),
    ('PERMISSION_READ'),
    ('AUDIT_READ'),
    -- Permissões técnicas exclusivas (somente ROLE_DEV)
    ('ROLE_CREATE'),
    ('ROLE_DELETE'),
    ('PERMISSION_CREATE'),
    ('PERMISSION_DELETE'),
    ('DEV_LOGS_TECHNICAL'),
    ('DEV_ROLE_MANAGE'),
    ('DEV_PERMISSION_MANAGE'),
    ('DEV_SYSTEM_CONFIG'),
    ('DEV_DEBUG_ENDPOINTS')
ON CONFLICT (name) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. ROLE_ADMIN — apenas permissões de produto (sem criar/deletar roles/perms)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'USER_CREATE', 'USER_READ', 'USER_UPDATE', 'USER_DELETE',
    'USER_ROLE_ASSIGN', 'USER_STATUS',
    'ROLE_READ', 'ROLE_MANAGE_PERMISSIONS',
    'PERMISSION_READ',
    'AUDIT_READ'
)
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. ROLE_DEV — herda ADMIN + todas as permissões técnicas exclusivas
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_DEV'
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Usuários
--    !! SUBSTITUA O HASH ABAIXO ANTES DE EXECUTAR !!
--    O mesmo hash é usado para admin e dev — troque a senha de cada um após
--    o primeiro login.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO users (username, password, enabled, email_verified, email)
VALUES
    ('admin',
     '$2b$10$OQVbkT2eHUkt1sSn00p/OesZlENiQJIvlb2jfi6kyjlFqhQANJ9h6',
     TRUE, TRUE, NULL),
    ('dev',
     '$2b$10$OQVbkT2eHUkt1sSn00p/OesZlENiQJIvlb2jfi6kyjlFqhQANJ9h6',
     TRUE, TRUE, NULL)
ON CONFLICT (username) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Atribuição de roles aos usuários
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_ADMIN'
WHERE u.username = 'admin'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_DEV'
WHERE u.username = 'dev'
ON CONFLICT DO NOTHING;

COMMIT;

-- ─────────────────────────────────────────────────────────────────────────────
-- Verificação: exibe estado dos usuários criados
-- ─────────────────────────────────────────────────────────────────────────────
SELECT
    u.username,
    u.enabled,
    u.email_verified,
    string_agg(r.name, ', ' ORDER BY r.name) AS roles,
    COUNT(p.id) AS num_permissions
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles r       ON r.id = ur.role_id
LEFT JOIN role_permissions rp ON rp.role_id = r.id
LEFT JOIN permissions p ON p.id = rp.permission_id
WHERE u.username IN ('admin', 'dev')
GROUP BY u.username, u.enabled, u.email_verified
ORDER BY u.username;
