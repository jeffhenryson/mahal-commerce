-- Vínculo opcional entre stock_movement e o goods_receipt que originou a ENTRADA (item 2 do
-- pedido do frontend — histórico de compras por SKU).
--
-- Até aqui não existia FK nenhuma entre as duas tabelas: ComprasService.receiveGoods criava uma
-- ENTRADA por item via EstoqueUseCase.adjustStock e salvava o GoodsReceipt depois, sem passar o
-- id adiante. unitCost/lotCode já ficam gravados na própria movimentação (EST-F007/EST-F008), mas
-- supplierId/supplierName não tinham caminho nenhum — o dialog "Anotar reposição" do frontend
-- precisa dessa referência para "quanto pedir" fazer sentido.
--
-- Aditivo, mesma régua das demais colunas de vínculo deste módulo: NULL em toda movimentação
-- manual (sem recebimento associado) e em toda ENTRADA anterior a esta migration — limitação
-- conhecida, sem backfill retroativo, porque o dado não existia antes.

ALTER TABLE stock_movement ADD COLUMN goods_receipt_id BIGINT REFERENCES goods_receipt (id);

CREATE INDEX idx_stock_movement_goods_receipt_id ON stock_movement (goods_receipt_id)
    WHERE goods_receipt_id IS NOT NULL;

COMMENT ON COLUMN stock_movement.goods_receipt_id IS
    'Vínculo opcional com o recebimento que originou a ENTRADA. NULL em toda movimentação manual e em todo recebimento anterior a esta migration (limitação conhecida, dado histórico sem correção retroativa).';
