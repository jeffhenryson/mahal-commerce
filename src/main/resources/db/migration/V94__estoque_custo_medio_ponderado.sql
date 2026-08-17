-- EST-F007 — custo médio ponderado por SKU/depósito (docs/dominios/estoque/README.md, backlog).
--
-- average_cost é ADITIVO a stock_balance, mesmo espírito de reserved_quantity (EST-F021) e de
-- stock_lot em relação ao saldo agregado (EST-F008): não substitui product.cost_price (o input
-- MANUAL do lojista, usado em tempo real pelo PDV via Pricing.marginAmount/marginPercent), é uma
-- métrica adicional de valorização calculada a partir do histórico real de entradas. NULL até a
-- primeira ENTRADA que informar unit_cost — ausência é "nunca recebido com custo conhecido", não
-- zero (mesma convenção de Pricing).
ALTER TABLE stock_balance ADD COLUMN average_cost NUMERIC(14,2) NULL;

-- Custo unitário da entrada (opcional): alimenta o recálculo de stock_balance.average_cost e fica
-- gravado no ledger para auditoria/replay. Só ENTRADA pode ter valor — SAIDA/AJUSTE rejeitam
-- (UnexpectedUnitCostException), mesma régua de lot_code/expiry_date (validateLotInfo).
ALTER TABLE stock_movement ADD COLUMN unit_cost NUMERIC(14,2) NULL;

-- Custo unitário informado no recebimento de mercadoria (compras) — propagado para
-- EstoqueUseCase.adjustStock como unit_cost do movimento.
ALTER TABLE goods_receipt_item ADD COLUMN unit_cost NUMERIC(14,2) NULL;

COMMENT ON COLUMN stock_balance.average_cost IS
    'Custo médio ponderado móvel (EST-F007), recalculado a cada ENTRADA com unit_cost informado. Distinto de product.cost_price (input manual). NULL = nunca recebeu entrada com custo conhecido.';
