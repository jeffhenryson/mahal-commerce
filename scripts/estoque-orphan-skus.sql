-- =============================================================================================
-- EST-C011 — Levantamento de SKU órfão no estoque
-- =============================================================================================
--
-- O QUE É UM ÓRFÃO
-- Uma linha de stock_balance, stock_movement ou stock_reorder_point cujo `sku` não existe nem
-- em product.sku nem em product_variant.sku. Essas três tabelas guardam `sku` como texto livre:
-- não há FK para o catálogo, por decisão registrada em EST-C002 (stock_movement é histórico
-- imutável, e uma FK impediria arquivar ou renomear produto).
--
-- POR QUE ELES EXISTEM
-- Até EST-C002 (2026-07-27) nada validava o SKU antes de movimentar saldo. Um SKU digitado
-- errado no PDV ou em Compras criava saldo e ledger órfãos em silêncio. Desde aquela correção
-- `adjustStock` e `setReorderPoint` recusam SKU desconhecido com 404 PRODUCT_NOT_FOUND, então
-- nenhum órfão novo é criado — mas o passivo anterior continua gravado.
--
-- ⚠️ NÃO EXPURGUE SEM CONFERIR, SKU A SKU.
-- Os dois destinos possíveis são incompatíveis e a consulta não distingue um do outro:
--   (a) o produto existia e foi apagado / nunca chegou a ser cadastrado  → CADASTRAR o produto
--       faltante (POST /estoque/products) e manter o histórico, que é legítimo;
--   (b) o SKU foi digitado errado e nunca existiu                        → EXPURGAR as linhas.
-- Apagar em massa destrói o histórico do caso (a). É por isso que a aplicação só oferece a
-- leitura: GET /estoque/integrity/orphan-skus, com permissão ESTOQUE_STOCK_MANAGE.
--
-- COMO USAR ESTE ARQUIVO
--   psql "$DATABASE_URL" -f scripts/estoque-orphan-skus.sql
-- Rode o LEVANTAMENTO, decida o destino de cada SKU, e só então use o bloco de EXPURGO —
-- que está comentado de propósito.
-- =============================================================================================


-- ---------------------------------------------------------------------------------------------
-- 1. LEVANTAMENTO — é isto que se roda. Somente leitura.
--
-- `quantity` zero com `movement_count` alto é o caso típico: a venda deu baixa até zerar e a
-- linha de saldo sobrou. `last_movement_at` antigo indica dado morto; recente indica que algo
-- ainda escreve nesse SKU e a origem precisa ser investigada antes de qualquer limpeza.
-- ---------------------------------------------------------------------------------------------
SELECT o.sku,
       w.code                                        AS warehouse_code,
       COALESCE(b.quantity, 0)                       AS quantity,
       (SELECT COUNT(*) FROM stock_movement m
         WHERE m.sku = o.sku AND m.warehouse_id = o.warehouse_id) AS movement_count,
       CASE WHEN r.id IS NULL THEN FALSE ELSE TRUE END           AS has_reorder_point,
       (SELECT MAX(m.created_at) FROM stock_movement m
         WHERE m.sku = o.sku AND m.warehouse_id = o.warehouse_id) AS last_movement_at
FROM (SELECT sku, warehouse_id FROM stock_balance
      UNION
      SELECT sku, warehouse_id FROM stock_movement
      UNION
      SELECT sku, warehouse_id FROM stock_reorder_point) o
JOIN warehouse w ON w.id = o.warehouse_id
LEFT JOIN stock_balance b       ON b.sku = o.sku AND b.warehouse_id = o.warehouse_id
LEFT JOIN stock_reorder_point r ON r.sku = o.sku AND r.warehouse_id = o.warehouse_id
WHERE NOT EXISTS (SELECT 1 FROM product p         WHERE p.sku = o.sku)
  AND NOT EXISTS (SELECT 1 FROM product_variant v WHERE v.sku = o.sku)
ORDER BY o.sku, w.code;


-- ---------------------------------------------------------------------------------------------
-- 2. RESUMO — quantos SKUs distintos e quantas linhas por tabela.
-- Use para dimensionar o trabalho antes de abrir a lista inteira.
-- ---------------------------------------------------------------------------------------------
SELECT (SELECT COUNT(DISTINCT sku) FROM stock_balance sb
         WHERE NOT EXISTS (SELECT 1 FROM product p         WHERE p.sku = sb.sku)
           AND NOT EXISTS (SELECT 1 FROM product_variant v WHERE v.sku = sb.sku)) AS skus_orfaos_em_saldo,
       (SELECT COUNT(*) FROM stock_balance sb
         WHERE NOT EXISTS (SELECT 1 FROM product p         WHERE p.sku = sb.sku)
           AND NOT EXISTS (SELECT 1 FROM product_variant v WHERE v.sku = sb.sku)) AS linhas_stock_balance,
       (SELECT COUNT(*) FROM stock_movement sm
         WHERE NOT EXISTS (SELECT 1 FROM product p         WHERE p.sku = sm.sku)
           AND NOT EXISTS (SELECT 1 FROM product_variant v WHERE v.sku = sm.sku)) AS linhas_stock_movement,
       (SELECT COUNT(*) FROM stock_reorder_point rp
         WHERE NOT EXISTS (SELECT 1 FROM product p         WHERE p.sku = rp.sku)
           AND NOT EXISTS (SELECT 1 FROM product_variant v WHERE v.sku = rp.sku)) AS linhas_reorder_point;


-- ---------------------------------------------------------------------------------------------
-- 3. EXPURGO — COMENTADO DE PROPÓSITO.
--
-- Só descomente depois de ter decidido, SKU a SKU, quais são digitação errada (destino (b)).
-- Preencha a lista explicitamente: NÃO troque o IN (...) por "todos os órfãos", porque isso
-- apagaria também o histórico legítimo dos SKUs do destino (a).
--
-- Rode dentro de uma transação e confira as contagens antes do COMMIT:
--
--   BEGIN;
--
--   -- Substitua pela lista conferida. Uma linha por SKU, para revisão em code review / PR.
--   -- DELETE FROM stock_reorder_point WHERE sku IN ('SKU-ERRADO-1', 'SKU-ERRADO-2');
--   -- DELETE FROM stock_movement      WHERE sku IN ('SKU-ERRADO-1', 'SKU-ERRADO-2');
--   -- DELETE FROM stock_balance       WHERE sku IN ('SKU-ERRADO-1', 'SKU-ERRADO-2');
--
--   -- Confira que o número de linhas apagadas bate com o levantamento e que nenhum SKU
--   -- legítimo entrou na lista. Se algo divergir:
--   -- ROLLBACK;
--   -- COMMIT;
--
-- Para o destino (a) não há SQL: cadastre o produto que falta pela API
-- (POST /estoque/products, permissão ESTOQUE_PRODUCT_MANAGE) e o SKU deixa de ser órfão
-- sozinho, sem tocar em nenhuma linha de estoque.
-- ---------------------------------------------------------------------------------------------
