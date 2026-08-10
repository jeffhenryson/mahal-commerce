-- Preço próprio por variação (EST-F020 / §P1-Estoque do BACKEND_TODO do mahal-admin).
--
-- Até aqui a Pricing morava só no SKU pai e toda a grade herdava (V63). Isso continua sendo o
-- PADRÃO e o caso comum — sabores da mesma essência custam o mesmo. Estas colunas cobrem a
-- exceção real: o vidro de 100g custar mais que o de 50g dentro da mesma grade.
--
-- Todas NULLABLE e sem DEFAULT: as quatro nulas são exatamente como "esta variação herda do pai"
-- fica representada. Um DEFAULT 0 aqui faria toda variação existente nascer valendo zero e o PDV
-- venderia de graça — é o mesmo raciocínio da V63, que também recusou backfill.
--
-- Mesmas precisões e mesmos CHECKs da V63/V85 no produto pai: preço negativo não é preço, e o
-- domínio já barra, mas o schema é a barreira que sobrevive a carga direta no banco.

ALTER TABLE product_variant ADD COLUMN cost_price     NUMERIC(14,2);
ALTER TABLE product_variant ADD COLUMN markup_percent NUMERIC(9,4);
ALTER TABLE product_variant ADD COLUMN sale_price     NUMERIC(14,2);
ALTER TABLE product_variant ADD COLUMN original_price NUMERIC(14,2);

ALTER TABLE product_variant
    ADD CONSTRAINT ck_product_variant_cost_price_non_negative     CHECK (cost_price     IS NULL OR cost_price     >= 0),
    ADD CONSTRAINT ck_product_variant_markup_percent_non_negative CHECK (markup_percent IS NULL OR markup_percent >= 0),
    ADD CONSTRAINT ck_product_variant_sale_price_non_negative     CHECK (sale_price     IS NULL OR sale_price     >= 0),
    ADD CONSTRAINT ck_product_variant_original_price_non_negative CHECK (original_price IS NULL OR original_price >= 0);

COMMENT ON COLUMN product_variant.cost_price IS
    'Custo próprio da variação. NULL nas quatro colunas de preço = herda a precificação do produto pai (EST-F020).';
COMMENT ON COLUMN product_variant.markup_percent IS
    'Markup próprio sobre o CUSTO, em %. Não confundir com margem, que é sobre a venda.';
COMMENT ON COLUMN product_variant.sale_price IS
    'Preço praticado próprio da variação. Quando informado, vence sobre o sugerido pelo markup.';
COMMENT ON COLUMN product_variant.original_price IS
    'Preço "de" riscado na vitrine para esta variação. Valor de exibição, sem relação obrigatória com o praticado.';
