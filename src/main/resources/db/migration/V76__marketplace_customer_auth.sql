-- ECM-F001 (Fatia 8, plano-pdv-marketplace.md §2.9) — cliente do marketplace reaproveita
-- UserEntity com um discriminador, em vez de uma entidade de autenticação separada. O motivo é
-- de custo/risco, não elegância: refazer hash de senha, verificação de email, TOTP, rotação de
-- refresh token e OAuth para o cliente final seria o erro mais caro disponível no projeto.
--
-- A colisão de username entre cliente e operador (ex.: os dois escolhem "joao") é o risco real
-- dessa decisão. O schema abaixo permite a coexistência via constraint composta — mas a
-- aplicação (UserService) NUNCA deixa essa colisão acontecer de fato: o registro de cliente
-- checa username/email contra AMBOS os tipos antes de criar a conta. A constraint composta é
-- rede de segurança, não o mecanismo principal — por isso nenhuma outra peça da stack de
-- autenticação (rate limit, lockout, refresh token, TOTP, blocklist), hoje chaveada só por
-- username, precisa mudar.

ALTER TABLE users ADD COLUMN user_type VARCHAR(20) NOT NULL DEFAULT 'OPERATOR';
ALTER TABLE users ADD COLUMN customer_id BIGINT REFERENCES customers(id);

ALTER TABLE users ADD CONSTRAINT ck_users_type CHECK (user_type IN ('OPERATOR', 'CUSTOMER'));
ALTER TABLE users ADD CONSTRAINT ck_users_customer_link
    CHECK ((user_type = 'CUSTOMER') = (customer_id IS NOT NULL));

CREATE UNIQUE INDEX uk_users_customer_id ON users (customer_id) WHERE customer_id IS NOT NULL;

-- Substitui a unicidade simples de username pela composta com o discriminador.
ALTER TABLE users DROP CONSTRAINT uk_user_username;
ALTER TABLE users ADD CONSTRAINT uk_user_username UNIQUE (user_type, username);

INSERT INTO roles (name) VALUES ('ROLE_CUSTOMER') ON CONFLICT (name) DO NOTHING;

-- Permissões mínimas do cliente final — Fatia 9 (carrinho/checkout) é quem de fato as usa, mas
-- entram já aqui junto com a role, no mesmo molde de V70/V71 (cashback e reembolso semearam
-- parâmetros/colunas antes do código que os consome existir).
INSERT INTO permissions (name) VALUES ('SHOP_CART_OWN'), ('SHOP_ORDER_OWN'), ('SHOP_CASHBACK_OWN')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_CUSTOMER' AND p.name IN ('SHOP_CART_OWN', 'SHOP_ORDER_OWN', 'SHOP_CASHBACK_OWN')
ON CONFLICT DO NOTHING;
