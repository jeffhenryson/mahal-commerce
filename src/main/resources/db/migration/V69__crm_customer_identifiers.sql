-- CRM-C005 — cliente identificável no balcão sem precisar inventar um e-mail.
--
-- Modelo revisto com o dono em 2026-07-29 (diverge do §2.5 do plano, que só previa email OU cpf):
-- CPF é o identificador OFICIAL do cadastro; email e contato (telefone) são identificadores
-- alternativos, qualquer um dos três basta para o cliente existir. A venda anônima de balcão
-- continua não exigindo nenhum — o gatilho é o operador querer identificar o cliente ("CPF na
-- nota?"), não a venda em si.

ALTER TABLE customers ALTER COLUMN email DROP NOT NULL;
ALTER TABLE customers ALTER COLUMN contato DROP NOT NULL;

-- UNIQUE padrão já basta aqui: a SQL trata cada NULL como distinto dos demais, então múltiplos
-- clientes sem CPF continuam permitidos. Não precisa de índice PARCIAL (CREATE UNIQUE INDEX ...
-- WHERE), que o H2 do perfil dev não suporta — diferente do Postgres, mas o schema de teste é
-- montado por ddl-auto a partir das entities, então a diferença importaria se fosse usada.
ALTER TABLE customers ADD CONSTRAINT uk_customers_cpf UNIQUE (cpf);

ALTER TABLE customers ADD CONSTRAINT ck_customers_has_identifier
    CHECK (cpf IS NOT NULL OR email IS NOT NULL OR contato IS NOT NULL);

COMMENT ON COLUMN customers.cpf IS
    'Identificador OFICIAL do cadastro. Cliente sem CPF ("cliente leve", achado só por email/contato) é válido e pesquisável, mas não é elegível a cashback quando o programa existir (Fatia 4) — ver Customer.isOfficiallyRegistered(). Cadastro rápido de balcão pode promover a oficial depois, informando o CPF.';

INSERT INTO permissions (name) VALUES ('CRM_CUSTOMER_LOOKUP')
ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'CRM_CUSTOMER_LOOKUP'
ON CONFLICT DO NOTHING;
