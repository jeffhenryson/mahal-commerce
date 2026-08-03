-- EST-F008 — lote e validade: fecha os dois pontos que ficaram sem lote depois da V74
-- (docs/dominios/estoque/proximos-passos.md).
--
-- goods_receipt_item ganha lot_code/expiry_date porque é por aqui que todo SKU lote-rastreado
-- entra no sistema (ComprasService.receiveGoods) — sem os campos, receber um SKU desses sempre
-- lançava MissingLotInfoException e abortava o recebimento inteiro.
--
-- stock_count_item ganha lot_code porque o balanço de inventário de um SKU lote-rastreado conta
-- cada lote separadamente, não o agregado — é o que permite reconciliar cada StockLot contra o
-- que foi contado na prateleira (EstoqueService.closeStockCount), em vez de só o stock_balance
-- agregado. A unicidade condicional (um SKU só pode ter uma linha sem lote, ou uma por lote) não
-- é expressável em CHECK/UNIQUE simples — por isso os dois índices únicos parciais abaixo, no
-- mesmo molde de uk_cashback_rate_active_scope (V69).

ALTER TABLE goods_receipt_item ADD COLUMN lot_code VARCHAR(50) NULL;
ALTER TABLE goods_receipt_item ADD COLUMN expiry_date DATE NULL;

ALTER TABLE stock_count_item ADD COLUMN lot_code VARCHAR(50) NULL;

ALTER TABLE stock_count_item DROP CONSTRAINT uk_stock_count_item_count_sku;

-- SKU não lote-rastreado: no máximo uma linha por balanço, como sempre foi.
CREATE UNIQUE INDEX uk_stock_count_item_sku_no_lot
    ON stock_count_item (stock_count_id, sku) WHERE lot_code IS NULL;
-- SKU lote-rastreado: no máximo uma linha por (balanço, SKU, lote) — recontar o mesmo lote
-- sobrescreve, contar um lote diferente do mesmo SKU acrescenta linha.
CREATE UNIQUE INDEX uk_stock_count_item_sku_lot
    ON stock_count_item (stock_count_id, sku, lot_code) WHERE lot_code IS NOT NULL;
