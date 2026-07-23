CREATE TABLE stock_movement (
    id           BIGSERIAL      PRIMARY KEY,
    sku          VARCHAR(50)    NOT NULL,
    warehouse_id BIGINT         NOT NULL REFERENCES warehouse(id) ON DELETE CASCADE,
    type         VARCHAR(10)    NOT NULL,
    quantity     NUMERIC(14,3)  NOT NULL,
    reason       VARCHAR(255)   NOT NULL,
    username     VARCHAR(80)    NOT NULL,
    created_at   TIMESTAMP      NOT NULL
);

CREATE INDEX idx_stock_movement_sku_warehouse_created ON stock_movement (sku, warehouse_id, created_at);
