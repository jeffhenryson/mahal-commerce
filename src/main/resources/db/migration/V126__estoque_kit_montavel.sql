-- EST-F031 / ECM-F008 / PDV-F019 — kit montável ("Kit Mahal"): o cliente escolhe bag, seda,
-- piteira, tubeck, tesoura, cuia e isqueiro, e paga a soma dos itens menos um desconto %.
--
-- NÃO é o kit de EST-F015 (product.type = 'KIT', receita fixa em product_kit_component). Aqui a
-- receita muda a cada venda, então o modelo não tem SKU nem saldo: cada item escolhido vira uma
-- linha comum de cart_item/comanda_item, agrupada por kit_bundle_id, e o estoque baixa item a item
-- pelo caminho de sempre.

CREATE TABLE kit_template (
    id                     BIGSERIAL    PRIMARY KEY,
    name                   VARCHAR(100) NOT NULL,
    description            TEXT,
    image_url              VARCHAR(500),
    discount_percent       NUMERIC(5,2) NOT NULL DEFAULT 0,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    visible_in_pos         BOOLEAN      NOT NULL DEFAULT TRUE,
    visible_in_marketplace BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_kit_template_discount_range CHECK (discount_percent >= 0 AND discount_percent < 100)
);

-- Mesma regra de product_category (V90): o nome é único sem caixa.
CREATE UNIQUE INDEX uk_kit_template_name_lower ON kit_template (LOWER(name));

-- O passo aponta para CATEGORIA, não para uma lista de SKUs: produto novo de seda entra no passo
-- "Seda" sem ninguém lembrar de editar o kit. required_step e não "required" para não depender de
-- palavra reservada em nenhum dos dois bancos.
CREATE TABLE kit_template_step (
    id              BIGSERIAL    PRIMARY KEY,
    kit_template_id BIGINT       NOT NULL REFERENCES kit_template (id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    display_order   INTEGER      NOT NULL DEFAULT 0,
    category_id     BIGINT       NOT NULL REFERENCES product_category (id),
    required_step   BOOLEAN      NOT NULL DEFAULT FALSE,
    max_items       INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT ck_kit_template_step_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_kit_template_step_max_items CHECK (max_items >= 1)
);
CREATE INDEX idx_kit_template_step_template ON kit_template_step (kit_template_id);

-- Carrinho: o pacote é um grupo de linhas. kit_step_id é guardado porque o checkout RECOTA o kit
-- (o carrinho nunca guarda preço, ECM-F003) e a cotação valida cada item contra o passo em que
-- foi escolhido. Sem FK para kit_template: apagar o modelo não pode quebrar carrinho — o checkout
-- recusa o pacote com KIT_NOT_AVAILABLE, e o cliente remove.
ALTER TABLE cart_item ADD COLUMN kit_bundle_id   VARCHAR(36);
ALTER TABLE cart_item ADD COLUMN kit_template_id BIGINT;
ALTER TABLE cart_item ADD COLUMN kit_step_id     BIGINT;
ALTER TABLE cart_item ADD CONSTRAINT ck_cart_item_kit_fields CHECK (
    (kit_bundle_id IS NULL AND kit_template_id IS NULL AND kit_step_id IS NULL)
    OR (kit_bundle_id IS NOT NULL AND kit_template_id IS NOT NULL AND kit_step_id IS NOT NULL));

-- A unicidade (cart_id, sku) valia para linha avulsa, e continua valendo só para ela: a mesma seda
-- pode estar avulsa E dentro de um kit, ou em dois kits.
ALTER TABLE cart_item DROP CONSTRAINT uk_cart_item_cart_sku;
CREATE UNIQUE INDEX uk_cart_item_cart_sku ON cart_item (cart_id, sku) WHERE kit_bundle_id IS NULL;
CREATE INDEX idx_cart_item_kit_bundle ON cart_item (cart_id, kit_bundle_id) WHERE kit_bundle_id IS NOT NULL;

-- Comanda: o preço já é congelado no lançamento, então basta o grupo e a parte do desconto do kit
-- que coube a cada linha. Desconto em coluna própria, não um unit_price menor — mesma razão de
-- surcharge_amount (V116) e de order_item.discount_amount: o bruto tem que continuar legível.
ALTER TABLE comanda_item ADD COLUMN kit_bundle_id       VARCHAR(36);
ALTER TABLE comanda_item ADD COLUMN kit_template_id     BIGINT;
ALTER TABLE comanda_item ADD COLUMN kit_discount_amount NUMERIC(14,2);
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_kit_fields CHECK (
    (kit_bundle_id IS NULL AND kit_template_id IS NULL AND kit_discount_amount IS NULL)
    OR (kit_bundle_id IS NOT NULL AND kit_template_id IS NOT NULL AND kit_discount_amount IS NOT NULL
        AND kit_discount_amount >= 0));

INSERT INTO permissions (name) VALUES ('ESTOQUE_KIT_TEMPLATE_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'ESTOQUE_KIT_TEMPLATE_MANAGE'
ON CONFLICT DO NOTHING;

COMMENT ON TABLE kit_template IS
    'Kit montável (EST-F031). Sem SKU nem saldo: a venda grava os itens escolhidos como linhas comuns agrupadas por kit_bundle_id.';
COMMENT ON COLUMN cart_item.kit_bundle_id IS
    'Pacote de kit montável ao qual a linha pertence. NULL = linha avulsa.';
COMMENT ON COLUMN comanda_item.kit_discount_amount IS
    'Parte do desconto do kit montável rateada para esta linha. Somada ao desconto de conta no fechamento.';
