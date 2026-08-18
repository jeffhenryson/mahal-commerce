-- Status de publicação do produto/kit (EST-F023) — rascunho de produto/kit pedido pelo dono.
-- DEFAULT 'ATIVO' cobre o backfill do catálogo existente sem UPDATE separado (nunca houve
-- rascunho antes desta feature).
ALTER TABLE product ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ATIVO';

-- Índice de igualdade, mesmo padrão de V92 para o filtro `type`.
CREATE INDEX IF NOT EXISTS idx_product_status ON product (status);
