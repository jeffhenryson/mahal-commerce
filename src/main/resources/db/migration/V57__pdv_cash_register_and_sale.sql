CREATE TABLE cash_register_session (
    id             BIGSERIAL      PRIMARY KEY,
    operator       VARCHAR(80)    NOT NULL,
    opened_at      TIMESTAMP      NOT NULL,
    opening_amount NUMERIC(14,2)  NOT NULL,
    closed_at      TIMESTAMP,
    status         VARCHAR(10)    NOT NULL
);

CREATE TABLE cash_register_sale (
    id             BIGSERIAL      PRIMARY KEY,
    session_id     BIGINT         NOT NULL REFERENCES cash_register_session(id) ON DELETE CASCADE,
    warehouse_code VARCHAR(50)    NOT NULL,
    sold_at        TIMESTAMP      NOT NULL,
    total_amount   NUMERIC(14,2)  NOT NULL
);

CREATE INDEX idx_cash_register_sale_session_id ON cash_register_sale (session_id);

CREATE TABLE sale_item (
    id         BIGSERIAL      PRIMARY KEY,
    sale_id    BIGINT         NOT NULL REFERENCES cash_register_sale(id) ON DELETE CASCADE,
    sku        VARCHAR(50)    NOT NULL,
    quantity   NUMERIC(14,3)  NOT NULL,
    unit_price NUMERIC(14,2)  NOT NULL
);

CREATE INDEX idx_sale_item_sale_id ON sale_item (sale_id);

INSERT INTO permissions (name) VALUES ('PDV_SALE_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'PDV_SALE_MANAGE'
ON CONFLICT DO NOTHING;
