-- Cinco novos campos de merchandising no catálogo de produtos:
--
-- 1. original_price — preço "de" riscado na vitrine ("de/por"). Puramente valor de exibição,
--    sem checagem relacional contra sale_price: o domínio (Pricing.hasDiscount) decide na
--    leitura se o desconto exibido faz sentido, não o schema na escrita.
-- 2. super_promo — segundo selo de destaque, distinto de on_sale (V83). Boolean simples, não
--    enum: on_sale já é parâmetro de filtro em GET /shop/catalog, e trocar para enum exigiria
--    reescrever esse filtro público sem necessidade.
-- 3. video_url — link de vídeo cadastrado manualmente, mesma convenção Estágio 01 de image_url.
-- 4. description — descrição longa do produto, sem limite curto como name.
-- 5. product_image — galeria de até 5 imagens ordenadas (validação de tamanho na API, não aqui).

ALTER TABLE product ADD COLUMN original_price NUMERIC(14,2);
ALTER TABLE product ADD CONSTRAINT ck_product_original_price_non_negative
    CHECK (original_price IS NULL OR original_price >= 0);

ALTER TABLE product ADD COLUMN super_promo BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE product ADD COLUMN video_url VARCHAR(2048);

ALTER TABLE product ADD COLUMN description TEXT;

COMMENT ON COLUMN product.original_price IS
    'Preço "de" riscado na vitrine, para efeito de desconto ("de/por"). Sem relação obrigatória com sale_price. NULL = sem preço "de" cadastrado.';
COMMENT ON COLUMN product.super_promo IS
    'Selo de destaque distinto de on_sale — segundo nível de promoção, sem filtro de query próprio.';
COMMENT ON COLUMN product.video_url IS
    'Link de vídeo cadastrado manualmente pelo lojista (Estágio 01 admin), mesma convenção de image_url. NULL = sem vídeo.';
COMMENT ON COLUMN product.description IS
    'Descrição longa do produto, opcional. Sem limite curto como name.';

-- Galeria de até 5 imagens ordenadas. Tabela filha sem PK própria (mesmo padrão leve de
-- product_attribute — ver V44), mapeada como @ElementCollection + @OrderColumn: não há
-- requisito de CRUD independente por imagem, só substituição da lista inteira via PATCH.
CREATE TABLE product_image (
    product_id BIGINT NOT NULL,
    image_order INTEGER NOT NULL,
    url VARCHAR(2048) NOT NULL,
    CONSTRAINT pk_product_image PRIMARY KEY (product_id, image_order),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

COMMENT ON TABLE product_image IS
    'Galeria de imagens do produto, até 5 por produto (limite validado na API). image_order preserva a ordem de exibição.';
