-- Seed inicial de roles base para hml/prod.
-- NOTA: o nome deste arquivo (seed_admin_hml) é enganoso — ele NÃO cria nenhum usuário admin.
-- Apenas insere as roles ROLE_ADMIN e ROLE_USER como pré-requisito para demais migrations.
-- Para criar o primeiro admin em hml/prod use: scripts/create-first-admin.sql
-- Senhas hardcoded em migrations são um risco de segurança; por isso usuários são criados externamente.

INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_USER')
ON CONFLICT (name) DO NOTHING;
