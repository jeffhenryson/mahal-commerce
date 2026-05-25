-- Seed inicial de roles base para hml/prod.
-- Usuários NÃO são criados aqui — senhas hardcoded em migrations são um risco de segurança.
-- Use o script scripts/create-first-admin.sql para criar o admin inicial via pipeline de deploy.

INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_USER')
ON CONFLICT (name) DO NOTHING;
