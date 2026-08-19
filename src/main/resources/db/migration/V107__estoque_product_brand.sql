-- Marca como entidade, mesma dívida que categoria já resolveu na V90 (item 3 do pedido do
-- frontend de mahal-admin). product.brand era VARCHAR(100) solto desde a V82 — sem onde
-- pendurar renomear, desativar ou consolidar grafias divergentes ("Zomo"/"zomo"/"Zomo ").
--
-- A MUDANÇA É ADITIVA, mesma decisão da V90. product.brand (texto) CONTINUA existindo e continua
-- sendo devolvido nos DTOs: o mahal-market ainda lê aquele campo. O que entra é uma coluna
-- brand_id opcional; o texto passa a ser o nome denormalizado, mantido em sincronia pelo backend
-- (EstoqueService.resolveBrand e ProductRepository.renameBrand).
--
-- Mais simples que product_category: sem featured/display_order — marca não tem pedido de
-- destaque de vitrine, só o de consolidação (renomear, desativar, deduplicar).

CREATE TABLE product_brand (
    id     BIGSERIAL    PRIMARY KEY,
    name   VARCHAR(100) NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_product_brand_name UNIQUE (name)
);

-- Mesma razão da V90: a unicidade acima é sensível a caixa, mas a resolução por nome (o caminho
-- de compatibilidade, em que o admin ainda manda texto livre) é case-insensitive.
CREATE UNIQUE INDEX uk_product_brand_name_lower ON product_brand (LOWER(name));

ALTER TABLE product ADD COLUMN brand_id BIGINT;
ALTER TABLE product ADD CONSTRAINT fk_product_brand
    FOREIGN KEY (brand_id) REFERENCES product_brand (id);
CREATE INDEX idx_product_brand_id ON product (brand_id);

-- Backfill: cada valor distinto de product.brand vira uma marca de verdade. O agrupamento é por
-- LOWER(name) para não criar duplicata que o índice acima recusaria, e o nome gravado é o de
-- MIN(id), isto é, a primeira grafia que entrou no catálogo.
INSERT INTO product_brand (name, active)
SELECT DISTINCT ON (LOWER(TRIM(brand))) TRIM(brand), TRUE
FROM product
WHERE brand IS NOT NULL AND TRIM(brand) <> ''
ORDER BY LOWER(TRIM(brand)), id;

UPDATE product p
SET brand_id = b.id
FROM product_brand b
WHERE p.brand IS NOT NULL
  AND LOWER(TRIM(p.brand)) = LOWER(b.name);

-- Alinha o texto do produto à grafia canônica escolhida no backfill — mesma razão da V90.
UPDATE product p
SET brand = b.name
FROM product_brand b
WHERE p.brand_id = b.id AND p.brand <> b.name;

INSERT INTO permissions (name)
VALUES ('ESTOQUE_BRAND_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'ESTOQUE_BRAND_MANAGE'
ON CONFLICT DO NOTHING;

COMMENT ON TABLE product_brand IS
    'Marca do catálogo. Aditiva: product.brand (texto) permanece como nome denormalizado, mantido em sincronia pela aplicação.';
COMMENT ON COLUMN product.brand_id IS
    'Vínculo opcional com product_brand. NULL = produto ainda não vinculado, estado válido. O nome fica denormalizado em product.brand.';
