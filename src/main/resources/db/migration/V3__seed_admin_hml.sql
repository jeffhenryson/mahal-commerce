-- Seed inicial para hml: cria roles base e usuário admin
-- Senha padrão: Admin@hml1 (troque após o primeiro login)

INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_USER')
ON CONFLICT (name) DO NOTHING;

INSERT INTO users (username, password) VALUES
    ('admin', '$2b$10$7lHBiG7LKZtOhNIjwPUJhea9nlG5F5hVtbKUX5ied6NQyntSXQIPa')
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin'
  AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
