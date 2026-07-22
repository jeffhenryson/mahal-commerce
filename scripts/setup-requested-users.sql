-- =============================================================================
-- setup-requested-users.sql
-- Configure requested users: dev, admin, atendente
-- =============================================================================

BEGIN;

-- 1. Garante que os papeis basicos existam
INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_USER'),
    ('ROLE_DEV')
ON CONFLICT (name) DO NOTHING;

-- 2. Insere ou atualiza os usuários principais
INSERT INTO users (username, password, enabled, email_verified, auth_provider)
VALUES
    ('dev',
     '$2b$10$b8Q91HI9as00RJEwd102XOqJ21fsN4Xq1kXWB3fTNt0sNJ7xAI7Ey',
     TRUE, TRUE, 'LOCAL'),
    ('admin',
     '$2b$10$Lf6qOsD5uWpXQARH8P8MjOGSYk52pcBUQviX3Hqla0wiJxMLJ6G5m',
     TRUE, TRUE, 'LOCAL'),
    ('atendente',
     '$2b$10$u.KJRFHWghJICJ1HSa5VOuXnVl/LdOw5yLyCCBu8xE/o5NzcvkUdi',
     TRUE, TRUE, 'LOCAL')
ON CONFLICT (username) 
DO UPDATE SET 
    password = EXCLUDED.password, 
    enabled = TRUE, 
    email_verified = TRUE,
    deleted_at = NULL; -- Garante que não esteja logicamente deletado

-- 3. Limpa associações de roles antigas dos usuários em questão
DELETE FROM user_roles 
WHERE user_id IN (SELECT id FROM users WHERE username IN ('dev', 'admin', 'atendente'));

-- 4. Associa as roles corretas
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r 
WHERE u.username = 'dev' AND r.name = 'ROLE_DEV';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r 
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r 
WHERE u.username = 'atendente' AND r.name = 'ROLE_USER';

-- 5. Exclui o gestor (caso exista no banco)
DELETE FROM user_roles 
WHERE user_id IN (SELECT id FROM users WHERE username = 'gestor');

DELETE FROM users WHERE username = 'gestor';

COMMIT;

-- 6. Verificação final
SELECT
    u.username,
    u.enabled,
    u.email_verified,
    string_agg(r.name, ', ' ORDER BY r.name) AS roles
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles r       ON r.id = ur.role_id
WHERE u.username IN ('admin', 'dev', 'atendente', 'gestor')
GROUP BY u.username, u.enabled, u.email_verified
ORDER BY u.username;
