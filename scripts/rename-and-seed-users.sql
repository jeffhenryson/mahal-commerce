-- =============================================================================
-- rename-and-seed-users.sql
-- Renomeia os usuários de teste hml para os nomes em português (tipos de usuário):
--   dev -> desenvolvedor (ROLE_DEV)
--   admin -> administrador (ROLE_ADMIN)
--   atendente -> atendente (ROLE_ATENDENTE, mantém username, apenas reseta senha)
--   (novo) usuario (ROLE_USER)
-- Substitui scripts/setup-requested-users.sql (mantido por histórico).
-- =============================================================================

BEGIN;

UPDATE users
SET username = 'desenvolvedor',
    email = 'desenvolvedor@mahaltabacaria.com.br',
    password = '$2b$10$NOSj0BkAnOn.PxtvNbNSB.lTvN0RJ8lb5fSRMlNoRyNtItunLI8nW',
    enabled = TRUE, email_verified = TRUE, deleted_at = NULL
WHERE username = 'dev';

UPDATE users
SET username = 'administrador',
    email = 'administrador@mahaltabacaria.com.br',
    password = '$2b$10$pTQKyGVEqAUVW9x1JCQvxe3hwH/dSPviG..W6UaSeqd//vCGVPnJS',
    enabled = TRUE, email_verified = TRUE, deleted_at = NULL
WHERE username = 'admin';

UPDATE users
SET email = 'atendente@mahaltabacaria.com.br',
    password = '$2b$10$bx85CNi5sWPIn/8RWgNX2u/jSuKl6G9I9wX.kSPWH8kwcbh2OOOGK',
    enabled = TRUE, email_verified = TRUE, deleted_at = NULL
WHERE username = 'atendente';

INSERT INTO users (username, email, password, enabled, email_verified, auth_provider)
VALUES ('usuario', 'usuario@mahaltabacaria.com.br',
        '$2b$10$/W0r40gO5TQQpp4fSfgY5OdyCAdkgO5WV93cJE5nNlbccsOs2n6rm',
        TRUE, TRUE, 'LOCAL')
ON CONFLICT (user_type, username) DO UPDATE SET
    password = EXCLUDED.password, enabled = TRUE, email_verified = TRUE, deleted_at = NULL;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'usuario' AND r.name = 'ROLE_USER'
ON CONFLICT DO NOTHING;

COMMIT;

SELECT u.username, u.email, u.enabled, u.email_verified,
       string_agg(r.name, ', ' ORDER BY r.name) AS roles
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles r ON r.id = ur.role_id
WHERE u.username IN ('desenvolvedor', 'administrador', 'atendente', 'usuario')
GROUP BY u.username, u.email, u.enabled, u.email_verified
ORDER BY u.username;
