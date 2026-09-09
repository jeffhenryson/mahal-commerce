-- EST-C023 — revincula product.brand_id para a base que entrou DEPOIS da V107.
--
-- O QA de 06/09/2026 mediu, sobre os 195 produtos da base: 190 com marca em texto (product.brand),
-- ZERO com vínculo (product.brand_id), e as 73 marcas cadastradas todas com productCount = 0. Na
-- tela isso é a coluna MARCA em "—", o filtro de marca sem nada para filtrar e a página "Todas as
-- Marcas" listando 73 cards dizendo "0 SKUs".
--
-- NÃO é defeito de código, e a V107 não falhou: ela fez este mesmo backfill quando rodou, e o
-- caminho novo funciona fim a fim (o QA confirmou cadastrando um produto pela tela — o brandId foi
-- persistido e o productCount subiu). O que aconteceu é ordem: o catálogo de demonstração foi
-- semeado por `scripts/` DEPOIS da migration, inserindo direto em `product` com a marca em texto e
-- sem vínculo. Toda carga em massa que não passe pelo EstoqueService reabre o mesmo buraco — vale
-- corrigir o seed junto, senão esta migration só adia o problema.
--
-- Repete os três passos da V107 de forma idempotente: quem já está vinculado não é tocado.

-- 1. Marcas que existem no texto e ainda não existem como entidade.
INSERT INTO product_brand (name, active)
SELECT DISTINCT ON (LOWER(TRIM(p.brand))) TRIM(p.brand), TRUE
FROM product p
WHERE p.brand IS NOT NULL AND TRIM(p.brand) <> ''
  AND NOT EXISTS (
        SELECT 1 FROM product_brand b WHERE LOWER(b.name) = LOWER(TRIM(p.brand))
      )
ORDER BY LOWER(TRIM(p.brand)), p.id;

-- 2. O vínculo. O casamento é por LOWER(unaccent(...)) e não só LOWER(): a base tem grafia com e
--    sem acento para a mesma marca, o mesmo descompasso que EST-C020 corrigiu na busca. A extensão
--    unaccent vem da V122.
UPDATE product p
SET brand_id = b.id
FROM product_brand b
WHERE p.brand_id IS NULL
  AND p.brand IS NOT NULL
  AND LOWER(unaccent(TRIM(p.brand))) = LOWER(unaccent(b.name));

-- 3. Alinha o texto à grafia canônica da entidade — mesma razão da V107 e da V90.
UPDATE product p
SET brand = b.name
FROM product_brand b
WHERE p.brand_id = b.id AND p.brand <> b.name;

-- Produto sem marca nenhuma continua com brand_id NULL, que a V107 já documenta como estado
-- válido. Inventar marca para ele seria pior do que a coluna vazia.
