-- PDV-F017 — conta dividida: a marcação de "esta linha já foi cobrada" mora no ITEM, não na comanda.
--
-- Por que no item, e não num status novo de comanda: o ck_comanda_status_consistency da V104 já
-- exige, para ABERTA, closed_at NULL e order_id NULL. Guardando a cobrança na linha, uma comanda com
-- metade da conta paga continua legitimamente ABERTA — nenhum CHECK precisa ser dropado e nenhum
-- estado intermediário precisa existir. Quem responde "a mesa acabou?" é a ausência de linha aberta,
-- não uma coluna nova no cabeçalho.
--
-- sales_order.comanda_id NÃO tem UNIQUE (V113 criou só um índice), então vários pedidos apontando
-- para a mesma comanda já era possível pelo schema — é essa folga que a conta dividida usa.
ALTER TABLE comanda_item ADD COLUMN closed_in_order_id BIGINT REFERENCES sales_order(id);

CREATE INDEX idx_comanda_item_closed_in_order_id ON comanda_item (closed_in_order_id);

COMMENT ON COLUMN comanda_item.closed_in_order_id IS
    'Pedido que cobrou esta linha (PDV-F017, conta dividida). NULL = ainda em aberto, o estado de toda linha antes da V121. Com conta dividida, comanda.order_id passa a ser o pedido que ENCERROU a mesa; o conjunto completo dos pedidos dela sai por sales_order.comanda_id.';
