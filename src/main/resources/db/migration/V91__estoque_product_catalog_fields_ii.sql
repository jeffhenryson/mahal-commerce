-- Segundo lote de campos aditivos de catálogo (§P1-Estoque do BACKEND_TODO do mahal-admin):
-- código de barras/EAN, unidade de medida, testador, elegibilidade para kit, preço extraordinário
-- e visibilidade por canal (PDV/Marketplace). Todos aditivos — nenhum campo/comportamento
-- existente muda para produtos já cadastrados.

-- Código de barras/EAN (GTIN-8/12/14). Único quando informado — índice PARCIAL, não UNIQUE de
-- coluna, porque o campo é opcional e vários produtos sem código não podem colidir entre si.
ALTER TABLE product ADD COLUMN barcode VARCHAR(14);
CREATE UNIQUE INDEX uk_product_barcode ON product (barcode) WHERE barcode IS NOT NULL;

ALTER TABLE product_variant ADD COLUMN barcode VARCHAR(14);
CREATE UNIQUE INDEX uk_product_variant_barcode ON product_variant (barcode) WHERE barcode IS NOT NULL;

-- Unidade de medida. Default 'UN' preserva o comportamento implícito de todo produto já
-- cadastrado — não há ambiguidade a resolver no backfill, ao contrário de outros campos deste
-- módulo que precisaram de heurística (ex.: category em V90).
ALTER TABLE product ADD COLUMN unit VARCHAR(10) NOT NULL DEFAULT 'UN';

-- Testador/amostra — distinto de produto padrão da Mahal. Ortogonal a `type` (SIMPLES/KIT).
ALTER TABLE product ADD COLUMN sample_product BOOLEAN NOT NULL DEFAULT FALSE;

-- Elegibilidade para entrar como componente de kit (opt-in, flag de negócio — não confundir com a
-- regra de domínio que já exige `type = SIMPLES`).
ALTER TABLE product ADD COLUMN kit_component_eligible BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: todo SKU já usado como componente de algum kit continua elegível, preservando o
-- comportamento de PUT /estoque/products/{sku}/kit para receitas já cadastradas — sem isso, a
-- nova checagem em EstoqueService.defineKitRecipe rejeitaria, no primeiro reenvio idempotente,
-- uma receita que era válida ontem.
UPDATE product SET kit_component_eligible = TRUE
WHERE sku IN (SELECT DISTINCT component_sku FROM product_kit_component);

-- Preço extraordinário: valor adicional opcional destinado a uma causa, por fora do preço de
-- venda normal. Puramente informativo (ver Pricing.causeAmount) — não soma no effectivePrice.
ALTER TABLE product ADD COLUMN cause_amount NUMERIC(14, 2);
ALTER TABLE product ADD CONSTRAINT ck_product_cause_amount_non_negative
    CHECK (cause_amount IS NULL OR cause_amount >= 0);

ALTER TABLE product_variant ADD COLUMN cause_amount NUMERIC(14, 2);
ALTER TABLE product_variant ADD CONSTRAINT ck_product_variant_cause_amount_non_negative
    CHECK (cause_amount IS NULL OR cause_amount >= 0);

-- Visibilidade por canal. DEFAULT TRUE nos dois é obrigatório, não cosmético: é o único jeito de
-- todo produto já cadastrado preservar o comportamento implícito de hoje (visível em todo canal).
ALTER TABLE product ADD COLUMN visible_in_pos BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE product ADD COLUMN visible_in_marketplace BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN product.barcode IS
    'Código de barras/EAN (GTIN-8/12/14) do SKU pai. Único quando informado (índice parcial).';
COMMENT ON COLUMN product_variant.barcode IS
    'Código de barras/EAN próprio da variação. Único quando informado (índice parcial).';
COMMENT ON COLUMN product.unit IS
    'Unidade de medida (UN|CX|KG|G|L|ML|PCT|DZ). Default UN preserva o comportamento implícito do catálogo já cadastrado.';
COMMENT ON COLUMN product.sample_product IS
    'Testador/amostra, distinto de produto padrão da Mahal. Ortogonal a `type`.';
COMMENT ON COLUMN product.kit_component_eligible IS
    'Elegibilidade de negócio para entrar como componente de kit — opt-in, checado em EstoqueService.defineKitRecipe.';
COMMENT ON COLUMN product.cause_amount IS
    'Preço extraordinário: valor adicional opcional destinado a uma causa. Puramente informativo.';
COMMENT ON COLUMN product.visible_in_pos IS
    'Produto aparece no PDV. Default TRUE preserva o comportamento implícito do catálogo já cadastrado.';
COMMENT ON COLUMN product.visible_in_marketplace IS
    'Produto aparece no marketplace/app. Default TRUE preserva o comportamento implícito do catálogo já cadastrado.';
