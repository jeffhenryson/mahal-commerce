-- =============================================================================
-- create-first-admin.sql
-- Executa ao reiniciar o ambiente de homologação para garantir que existe
-- um usuário admin com todas as permissões necessárias.
-- Idempotente: seguro de rodar mais de uma vez (ON CONFLICT DO NOTHING).
-- =============================================================================
--
-- ANTES DE EXECUTAR: gere o hash bcrypt da sua senha escolhida com UMA das
-- opções abaixo e substitua o placeholder na INSERT de users:
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

-- 1. Garante que as roles base existem (idempotente — já criadas pela V3)
INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_USER')
ON CONFLICT (name) DO NOTHING;

-- 2. Garante que todas as permissions existem (idempotente — já criadas pelas V4/V7/V8/V9)
INSERT INTO permissions (name) VALUES
    ('USER_CREATE'),
    ('USER_READ'),
    ('USER_UPDATE'),
    ('USER_DELETE'),
    ('USER_ROLE_ASSIGN'),
    ('USER_STATUS'),
    ('ROLE_READ'),
    ('ROLE_CREATE'),
    ('ROLE_DELETE'),
    ('ROLE_MANAGE_PERMISSIONS'),
    ('PERMISSION_READ'),
    ('PERMISSION_CREATE'),
    ('PERMISSION_DELETE')
ON CONFLICT (name) DO NOTHING;

-- 3. Garante que ROLE_ADMIN tem todas as permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- 4. Cria o usuário admin
--    enabled = TRUE        → conta ativa imediatamente
--    email_verified = TRUE → sem fluxo de verificação de email para conta admin manual
--    email = NULL          → opcional; preencha se quiser receber notificações
--
-- !! SUBSTITUA O HASH ABAIXO ANTES DE EXECUTAR !!
INSERT INTO users (username, password, enabled, email_verified, email)
VALUES (
    'admin',
    '$2b$10$N/7RpqaorLnjXLOBjWEW2OxqLKkua5v6D2SIVTI5umepVK7ur9NMe',
    TRUE,
    TRUE,
    NULL
)
ON CONFLICT (username) DO NOTHING;

-- 5. Atribui ROLE_ADMIN ao usuário admin
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_ADMIN'
WHERE u.username = 'admin'
ON CONFLICT DO NOTHING;

COMMIT;

-- Verificação final: exibe o estado do admin criado
SELECT
    u.id,
    u.username,
    u.enabled,
    u.email_verified,
    string_agg(r.name, ', ' ORDER BY r.name) AS roles
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles r       ON r.id = ur.role_id
WHERE u.username = 'admin'
GROUP BY u.id, u.username, u.enabled, u.email_verified;
