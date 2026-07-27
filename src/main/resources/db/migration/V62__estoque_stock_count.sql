-- EST-F006 — balanço de inventário (contagem com sessão).
--
-- stock_count é a sessão de contagem de um depósito; stock_count_item guarda o que foi contado
-- por SKU. `expected_quantity` e `difference` ficam nulos enquanto a contagem está aberta: são
-- preenchidos no fechamento, com o saldo do sistema naquele instante. Guardá-los é o que permite
-- auditar a divergência depois — o saldo, uma vez ajustado, já não conta essa história.

CREATE TABLE stock_count (
    id           BIGSERIAL     PRIMARY KEY,
    warehouse_id BIGINT        NOT NULL REFERENCES warehouse(id) ON DELETE CASCADE,
    status       VARCHAR(20)   NOT NULL,
    username     VARCHAR(80)   NOT NULL,
    created_at   TIMESTAMP     NOT NULL,
    closed_at    TIMESTAMP
);

-- Serve à listagem por depósito e à busca da contagem aberta de um depósito.
CREATE INDEX idx_stock_count_warehouse_status ON stock_count (warehouse_id, status);

CREATE TABLE stock_count_item (
    id                BIGSERIAL      PRIMARY KEY,
    stock_count_id    BIGINT         NOT NULL REFERENCES stock_count(id) ON DELETE CASCADE,
    sku               VARCHAR(50)    NOT NULL,
    counted_quantity  NUMERIC(14,3)  NOT NULL,
    expected_quantity NUMERIC(14,3),
    difference        NUMERIC(14,3),
    -- Recontar um SKU sobrescreve o valor anterior; duas linhas para o mesmo SKU tornariam o
    -- fechamento ambíguo.
    CONSTRAINT uk_stock_count_item_count_sku UNIQUE (stock_count_id, sku)
);

CREATE INDEX idx_stock_count_item_count_id ON stock_count_item (stock_count_id);

-- Sem permissão nova: o balanço reusa ESTOQUE_STOCK_MANAGE, a mesma que já autoriza movimentar
-- saldo — fechar uma contagem é exatamente isso, em lote.
