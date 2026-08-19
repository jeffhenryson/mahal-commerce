-- 10_validacao.sql — smoke tests pós-carga. Nenhuma dessas queries deve retornar algo
-- inesperado; onde há uma expectativa clara, ela está no comentário acima da query.

-- Contagens por tabela (conferir contra as metas do config.py)
SELECT 'users (demo)' AS tabela, count(*) FROM users WHERE username LIKE 'demo.%'
UNION ALL SELECT 'customers', count(*) FROM customers
UNION ALL SELECT 'product (SIMPLES)', count(*) FROM product WHERE type = 'SIMPLES'
UNION ALL SELECT 'product (KIT)', count(*) FROM product WHERE type = 'KIT'
UNION ALL SELECT 'product_kit_component', count(*) FROM product_kit_component
UNION ALL SELECT 'supplier', count(*) FROM supplier
UNION ALL SELECT 'goods_receipt', count(*) FROM goods_receipt
UNION ALL SELECT 'nfe_import', count(*) FROM nfe_import
UNION ALL SELECT 'cash_register_session', count(*) FROM cash_register_session
UNION ALL SELECT 'comanda', count(*) FROM comanda
UNION ALL SELECT 'sales_order', count(*) FROM sales_order
UNION ALL SELECT 'sales_order (BALCAO)', count(*) FROM sales_order WHERE channel = 'BALCAO'
UNION ALL SELECT 'sales_order (MARKETPLACE)', count(*) FROM sales_order WHERE channel = 'MARKETPLACE'
UNION ALL SELECT 'order_item', count(*) FROM order_item
UNION ALL SELECT 'order_payment', count(*) FROM order_payment
UNION ALL SELECT 'cashback_entry (EARNED)', count(*) FROM cashback_entry WHERE type = 'EARNED'
UNION ALL SELECT 'cashback_entry (REVERSED)', count(*) FROM cashback_entry WHERE type = 'REVERSED'
UNION ALL SELECT 'stock_movement', count(*) FROM stock_movement
UNION ALL SELECT 'stock_balance', count(*) FROM stock_balance
UNION ALL SELECT 'stock_reorder_point', count(*) FROM stock_reorder_point
UNION ALL SELECT 'cash_flow_entry', count(*) FROM cash_flow_entry;

-- Distribuição de status dos pedidos (funil de fulfillment do marketplace deve aparecer aqui)
SELECT channel, status, count(*) FROM sales_order GROUP BY channel, status ORDER BY channel, status;

-- Estágios dos clientes CRM
SELECT estagio, count(*) FROM customers GROUP BY estagio ORDER BY estagio;

-- Nenhum saldo negativo (a aplicação nunca produziria isso — se aparecer, há bug no simulador)
SELECT sku, warehouse_id, quantity FROM stock_balance WHERE quantity < 0;

-- Nenhum kit com saldo próprio (kit é virtual — só os componentes têm stock_balance)
SELECT sb.sku FROM stock_balance sb JOIN product p ON p.sku = sb.sku WHERE p.type = 'KIT';

-- net_amount deve bater exatamente com total - desconto - cashback (já é CHECK de banco, aqui é
-- só uma segunda conferência rápida)
SELECT id, order_number FROM sales_order
WHERE net_amount <> round(total_amount - discount_amount - cashback_redeemed, 2);

-- Nenhum order_item de venda concluída sem custo congelado (senão o relatório de margem some)
SELECT oi.id FROM order_item oi
JOIN sales_order so ON so.id = oi.order_id
WHERE so.status NOT IN ('CRIADO', 'AGUARDANDO_PAGAMENTO', 'CANCELADO') AND oi.cost_price IS NULL;

-- cashback_entry só para cliente com CPF (replicando Customer.isOfficiallyRegistered() —
-- não deve retornar nenhuma linha)
SELECT ce.id FROM cashback_entry ce
JOIN customers c ON c.id = ce.customer_id
WHERE ce.type = 'EARNED' AND c.cpf IS NULL;

-- SKUs abaixo do ponto de reposição (deve ter pelo menos alguns — é o que a fase 08 gera)
SELECT sb.sku, sb.quantity, rp.min_quantity
FROM stock_balance sb
JOIN stock_reorder_point rp ON rp.sku = sb.sku AND rp.warehouse_id = sb.warehouse_id
WHERE sb.quantity < rp.min_quantity;

-- Resumo financeiro por categoria/direção
SELECT category, direction, status, count(*), round(sum(amount), 2) AS total
FROM cash_flow_entry GROUP BY category, direction, status ORDER BY category, direction, status;
