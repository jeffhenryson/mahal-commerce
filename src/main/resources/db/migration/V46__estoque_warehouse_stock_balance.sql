CREATE TABLE warehouse (
    id     BIGSERIAL     PRIMARY KEY,
    code   VARCHAR(50)   NOT NULL,
    name   VARCHAR(255)  NOT NULL,
    type   VARCHAR(20)   NOT NULL,
    active BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_warehouse_code UNIQUE (code)
);

CREATE TABLE stock_balance (
    id           BIGSERIAL      PRIMARY KEY,
    sku          VARCHAR(50)    NOT NULL,
    warehouse_id BIGINT         NOT NULL REFERENCES warehouse(id) ON DELETE CASCADE,
    quantity     NUMERIC(14,3)  NOT NULL DEFAULT 0,
    version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_balance_sku_warehouse UNIQUE (sku, warehouse_id)
);

CREATE INDEX idx_stock_balance_warehouse_id ON stock_balance (warehouse_id);
