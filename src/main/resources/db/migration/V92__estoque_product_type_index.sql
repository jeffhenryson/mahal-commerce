-- Índice de igualdade para o novo filtro `type` em GET /estoque/products (§P1-Estoque do
-- BACKEND_TODO do mahal-admin) — mesmo padrão de V87 para os demais filtros de igualdade do
-- catálogo (category/brand).
CREATE INDEX IF NOT EXISTS idx_product_type ON product (type);
