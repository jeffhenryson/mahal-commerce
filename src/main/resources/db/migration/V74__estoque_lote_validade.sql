-- EST-F008 — lote e validade (docs/dominios/estoque/README.md, backlog do módulo).
--
-- stock_lot é uma subdivisão ADITIVA do saldo agregado, não uma substituição: stock_balance
-- continua sendo a fonte da verdade de quantity/reserved_quantity, escrita e travada
-- (optimistic locking) exatamente como antes. Para um SKU lote-rastreado,
-- SUM(stock_lot.quantity) para o par (sku, warehouse) deve sempre igualar
-- stock_balance.quantity — mantido pela mesma transação de EstoqueService.adjustStock que já
-- escreve saldo e ledger juntos. Reserva (EST-F013/F021), kit (EST-F015) e ponto de reposição
-- (EST-F004) continuam lendo/escrevendo stock_balance sem nenhuma mudança.
--
-- Rastreamento é opt-in por produto (lot_tracked): nem todo SKU tem validade — só
-- essência/carvão/perecível, não narguilé ou acessório.

ALTER TABLE product ADD COLUMN lot_tracked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE stock_lot (
    id           BIGSERIAL     PRIMARY KEY,
    sku          VARCHAR(50)   NOT NULL,
    warehouse_id BIGINT        NOT NULL REFERENCES warehouse(id) ON DELETE CASCADE,
    lot_code     VARCHAR(50)   NOT NULL,
    expiry_date  DATE          NOT NULL,
    quantity     NUMERIC(14,3) NOT NULL DEFAULT 0,
    alerted_at   TIMESTAMP     NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_lot_sku_warehouse_lot UNIQUE (sku, warehouse_id, lot_code),
    CONSTRAINT ck_stock_lot_quantity_non_negative CHECK (quantity >= 0)
);

CREATE INDEX idx_stock_lot_warehouse_id ON stock_lot (warehouse_id);
-- Suporta a varredura do alerta de vencimento próximo (expiry_date <= cutoff).
CREATE INDEX idx_stock_lot_expiry_date ON stock_lot (expiry_date);

COMMENT ON TABLE stock_lot IS
    'Saldo por lote de um SKU lote-rastreado (EST-F008). Aditivo a stock_balance, nunca substituto: SUM(quantity) por (sku, warehouse_id) deve igualar stock_balance.quantity daquele par.';

-- Rastreia de qual lote veio cada ENTRADA (EST-F008). SAIDA não carimba lote — o consumo é FEFO
-- automático e pode atravessar mais de um lote, então não força uma relação 1:1 com stock_movement.
ALTER TABLE stock_movement ADD COLUMN lot_code VARCHAR(50) NULL;
