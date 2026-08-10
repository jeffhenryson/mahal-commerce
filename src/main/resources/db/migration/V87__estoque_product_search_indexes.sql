-- Índices de apoio à listagem filtrada do catálogo (GET /estoque/products com search/category/
-- brand/active). Antes desta entrega o endpoint só aceitava page/size e o admin filtrava em
-- memória sobre no máximo 100 registros; agora o filtro roda no banco e passa a valer a pena
-- indexar o que ele realmente usa.
--
-- Só entram os filtros de IGUALDADE. A busca textual é `LIKE '%termo%'`, com curinga à esquerda,
-- e um índice btree não serve para isso — o planner ignora e faz seq scan de qualquer jeito.
-- Criar um índice sobre LOWER(name) aqui daria a falsa impressão de que a busca está indexada.
-- Quando o catálogo crescer a ponto de o seq scan doer, a resposta certa é `CREATE EXTENSION
-- pg_trgm` + índice GIN sobre LOWER(name) e LOWER(sku); fica registrado aqui para não se
-- redescobrir o problema do zero.

CREATE INDEX IF NOT EXISTS idx_product_category_lower ON product (LOWER(category));
CREATE INDEX IF NOT EXISTS idx_product_brand_lower    ON product (LOWER(brand));

COMMENT ON INDEX idx_product_category_lower IS
    'Filtro ?category= de GET /estoque/products — comparação case-insensitive, por isso sobre LOWER()';
COMMENT ON INDEX idx_product_brand_lower IS
    'Filtro ?brand= de GET /estoque/products — comparação case-insensitive, por isso sobre LOWER()';
