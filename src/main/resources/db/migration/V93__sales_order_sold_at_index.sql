-- Índice de range para `sold_at` — usado pelo filtro from/to de GET /orders (findFiltered) e por
-- toda agregação de GET /orders/summary (countByStatus, findRevenueTotals, findRevenueByChannel,
-- findDailyRevenue). Não existia índice nessa coluna até aqui.
CREATE INDEX IF NOT EXISTS idx_sales_order_sold_at ON sales_order (sold_at);
