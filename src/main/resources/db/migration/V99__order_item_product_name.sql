-- Nome do produto congelado no item do pedido, no instante da venda (Docs/BACKEND_TODO.md do
-- mahal-admin, §"Vendas: productName ausente em OrderItemAdminResponseDto") — mesma razão de
-- cost_price: se o produto for renomeado depois, o histórico não pode mudar junto.
ALTER TABLE order_item ADD COLUMN product_name VARCHAR(255);
