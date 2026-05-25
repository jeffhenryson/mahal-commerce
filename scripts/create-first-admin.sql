-- Script de criação do primeiro admin — executar UMA VEZ no deploy inicial de hml/prod.
-- Substitua o hash abaixo pelo bcrypt da senha escolhida:
--   java -cp bcrypt.jar org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder <senha>
-- Ou use o endpoint POST /users com um token de bootstrap temporário.
--
-- Gerar hash bcrypt via Python (bcrypt library):
--   python3 -c "import bcrypt; print(bcrypt.hashpw(b'SUA_SENHA_AQUI', bcrypt.gensalt(10)).decode())"
--
-- Gerar hash bcrypt via htpasswd:
--   htpasswd -bnBC 10 "" SUA_SENHA_AQUI | tr -d ':\n'

-- 1. Cria o usuário admin com o hash da senha escolhida
INSERT INTO users (username, password)
VALUES ('admin', '$2a$10$SUBSTITUA_ESTE_HASH_POR_UM_GERADO_COM_A_SENHA_REAL')
ON CONFLICT (username) DO NOTHING;

-- 2. Garante que a role ROLE_ADMIN existe (já criada pela migration V3, mas idempotente)
INSERT INTO roles (name) VALUES ('ROLE_ADMIN')
ON CONFLICT (name) DO NOTHING;

-- 3. Atribui ROLE_ADMIN ao usuário admin
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
