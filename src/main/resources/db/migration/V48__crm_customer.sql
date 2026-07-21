CREATE TABLE customers (
    id            BIGSERIAL     PRIMARY KEY,
    nome          VARCHAR(255)  NOT NULL,
    contato       VARCHAR(30)   NOT NULL,
    email         VARCHAR(255)  NOT NULL,
    cpf           VARCHAR(11),
    origem        VARCHAR(100),
    cadastrado_em TIMESTAMP     NOT NULL,
    CONSTRAINT uk_customers_email UNIQUE (email)
);

INSERT INTO permissions (name) VALUES ('CRM_CUSTOMER_READ'), ('CRM_CUSTOMER_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('CRM_CUSTOMER_READ', 'CRM_CUSTOMER_MANAGE')
ON CONFLICT DO NOTHING;
