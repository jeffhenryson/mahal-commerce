-- Categoria como entidade, com destaque e ordem de vitrine (§P1-Estoque do BACKEND_TODO do
-- mahal-admin, item de maior alcance).
--
-- O pedido concreto: poder mandar uma categoria para a primeira linha do app. Isso é impossível
-- enquanto categoria for texto solto dentro do produto — não há onde pendurar "destaque" nem
-- "ordem" de algo que não existe como registro.
--
-- A MUDANÇA É ADITIVA, e essa é a decisão central desta migration. product.category (texto)
-- CONTINUA existindo e continua sendo devolvido nos DTOs: o mahal-market e o próprio mahal-admin
-- leem aquele campo hoje, e trocá-lo por uma FK quebraria os dois de uma vez. O que entra é uma
-- coluna category_id opcional; o texto passa a ser o nome denormalizado, mantido em sincronia
-- pelo backend (EstoqueService.resolveCategory e ProductRepository.renameCategory).
--
-- display_order e não "order": ORDER é palavra reservada em SQL.

CREATE TABLE product_category (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    featured      BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order INTEGER      NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_product_category_name UNIQUE (name),
    CONSTRAINT ck_product_category_display_order_non_negative CHECK (display_order >= 0)
);

-- A unicidade acima é sensível a caixa, mas a resolução por nome (o caminho de compatibilidade,
-- em que o admin ainda manda texto livre) é case-insensitive. Sem este índice, "Narguilé" e
-- "narguilé" passariam pela UNIQUE e virariam duas categorias que a aplicação trata como uma.
CREATE UNIQUE INDEX uk_product_category_name_lower ON product_category (LOWER(name));

ALTER TABLE product ADD COLUMN category_id BIGINT;
ALTER TABLE product ADD CONSTRAINT fk_product_category
    FOREIGN KEY (category_id) REFERENCES product_category (id);
CREATE INDEX idx_product_category_id ON product (category_id);

-- Backfill: cada valor distinto de product.category vira uma categoria de verdade. Sem destaque e
-- com ordem 0 — o lojista decide depois quais promover; adivinhar aqui seria inventar hierarquia.
-- O agrupamento é por LOWER(name) para não criar duplicata que o índice acima recusaria, e o nome
-- gravado é o de MIN(id), isto é, a primeira grafia que entrou no catálogo.
INSERT INTO product_category (name, featured, display_order, active)
SELECT DISTINCT ON (LOWER(TRIM(category))) TRIM(category), FALSE, 0, TRUE
FROM product
WHERE category IS NOT NULL AND TRIM(category) <> ''
ORDER BY LOWER(TRIM(category)), id;

UPDATE product p
SET category_id = c.id
FROM product_category c
WHERE p.category IS NOT NULL
  AND LOWER(TRIM(p.category)) = LOWER(c.name);

-- Alinha o texto do produto à grafia canônica escolhida no backfill. Sem isto, dois produtos
-- gravados como "Narguilé" e "narguilé" apontariam para a mesma categoria mas continuariam
-- exibindo rótulos diferentes na vitrine.
UPDATE product p
SET category = c.name
FROM product_category c
WHERE p.category_id = c.id AND p.category <> c.name;

-- A tabela `permissions` (V4__permissions.sql) só tem `id` e `name` — nunca teve `description`.
INSERT INTO permissions (name)
VALUES ('ESTOQUE_CATEGORY_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'ESTOQUE_CATEGORY_MANAGE'
ON CONFLICT DO NOTHING;

COMMENT ON TABLE product_category IS
    'Categoria do catálogo. Aditiva: product.category (texto) permanece como nome denormalizado, mantido em sincronia pela aplicação.';
COMMENT ON COLUMN product_category.featured IS
    'Categoria em destaque — a vitrine a coloca na primeira linha. Separado de display_order de propósito: destacar não pode exigir reordenar todo mundo.';
COMMENT ON COLUMN product_category.display_order IS
    'Posição relativa DENTRO do grupo (destacadas e não destacadas ordenam separadamente). Empate desempata por nome.';
COMMENT ON COLUMN product.category_id IS
    'Vínculo opcional com product_category. NULL = produto ainda não vinculado, estado válido. O nome fica denormalizado em product.category.';
