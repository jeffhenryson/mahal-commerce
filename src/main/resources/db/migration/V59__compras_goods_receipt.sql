CREATE TABLE goods_receipt (
    id             BIGSERIAL      PRIMARY KEY,
    supplier_id    BIGINT         NOT NULL REFERENCES supplier(id) ON DELETE RESTRICT,
    warehouse_code VARCHAR(50)    NOT NULL,
    username       VARCHAR(80)    NOT NULL,
    received_at    TIMESTAMP      NOT NULL
);

CREATE INDEX idx_goods_receipt_supplier_id ON goods_receipt (supplier_id);

CREATE TABLE goods_receipt_item (
    id               BIGSERIAL      PRIMARY KEY,
    goods_receipt_id BIGINT         NOT NULL REFERENCES goods_receipt(id) ON DELETE CASCADE,
    sku              VARCHAR(50)    NOT NULL,
    quantity         NUMERIC(14,3)  NOT NULL
);

CREATE INDEX idx_goods_receipt_item_receipt_id ON goods_receipt_item (goods_receipt_id);
