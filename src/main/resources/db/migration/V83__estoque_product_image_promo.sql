ALTER TABLE product ADD COLUMN image_url VARCHAR(2048);
ALTER TABLE product ADD COLUMN on_sale BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN product.image_url IS 'Link de imagem cadastrado manualmente pelo lojista (Estágio 01 admin). NULL = sem imagem.';
COMMENT ON COLUMN product.on_sale IS 'Produto em promoção (Estágio 01 admin) — usado pelo marketplace para montar a lista "Promoções".';
