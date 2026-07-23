CREATE TABLE stock_reorder_point (
    id           BIGSERIAL      PRIMARY KEY,
    sku          VARCHAR(50)    NOT NULL,
    warehouse_id BIGINT         NOT NULL REFERENCES warehouse(id) ON DELETE CASCADE,
    min_quantity NUMERIC(14,3)  NOT NULL,
    CONSTRAINT uk_stock_reorder_point_sku_warehouse UNIQUE (sku, warehouse_id)
);
