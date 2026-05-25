-- Seed inicial para hml: cria roles base, admin e usuário de teste
-- Admin: admin / Admin@dev1
-- User:  user  / User@dev1

INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_USER')
ON CONFLICT (name) DO NOTHING;

INSERT INTO users (username, password) VALUES
    ('admin', '$2a$10$9sRDWcMoLx6cQ/o/Ya5qIOy1idykPuSQpxORa8917JYrIhMUEcZXO'),
    ('user',  '$2a$10$phZSvEtUFNv2DkFBSL/9CuZbqFJbGz0APXff4CDco0xqTGr6LVb8y')
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'user' AND r.name = 'ROLE_USER'
ON CONFLICT DO NOTHING;
