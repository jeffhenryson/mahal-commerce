-- PDV-F011 — O que saiu para a mesa, e a essência que cobra a mais no open rosh.
--
-- Dois campos que não cabem em {sku, quantity, mode, courtesy, linkedItemId}:
--
--   notes            — registro livre do setup da sessão ("Narguilé grande · Com filtro · Pinça
--                      P-02"). SEM NENHUM EFEITO EM PREÇO. Existe porque a pinça não pode virar
--                      linha de cortesia: cortesia baixa estoque (a pinça não é consumida), exige
--                      PDV_COMANDA_COURTESY e apareceria no cupom do cliente como um item de R$ 0
--                      que ele não pediu. Registro não é venda a zero.
--
--   surcharge_amount — acréscimo decidido no balcão, caso a caso, para o sabor que sai mais caro
--                      mesmo dentro do consumo livre. NÃO é um discount_amount negativo: acréscimo
--                      e desconto são operações opostas com o mesmo peso contábil, e o relatório
--                      precisa distinguir "cobramos a mais" de "cobramos a menos".
--
-- O unit_price gravado JÁ INCLUI o acréscimo (base + surcharge) — senão o subtotal não fecha. A
-- coluna existe à parte para o relatório conseguir separar as duas parcelas depois, pela mesma
-- razão que discount_amount não vira "preço menor" em order_item.

-- ---------------------------------------------------------------------------------------------
-- 1. Linha da comanda
-- ---------------------------------------------------------------------------------------------
ALTER TABLE comanda_item ADD COLUMN notes            VARCHAR(200);
ALTER TABLE comanda_item ADD COLUMN surcharge_amount NUMERIC(14,2);

ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_surcharge_non_negative
    CHECK (surcharge_amount IS NULL OR surcharge_amount >= 0);

-- Uma linha que o cliente não paga não pode ter valor extra cobrado. A combinação é uma
-- contradição, não um caso de borda — irmã de ck_comanda_item_courtesy_is_free.
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_surcharge_not_on_courtesy
    CHECK (surcharge_amount IS NULL OR surcharge_amount = 0 OR courtesy = FALSE);

-- O acréscimo só existe no consumo livre. Nos demais modos a diferença do sabor caro já está no
-- pricing da própria variante, que é onde ela deve morar.
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_surcharge_only_open_rosh
    CHECK (surcharge_amount IS NULL OR surcharge_amount = 0 OR mode = 'OPEN_ROSH');

COMMENT ON COLUMN comanda_item.notes IS
    'Registro livre do setup da sessão (narguilé, filtro, qual pinça). Texto opaco: o servidor grava e devolve, nunca interpreta. Sem efeito em preço.';
COMMENT ON COLUMN comanda_item.surcharge_amount IS
    'Acréscimo somado ao openRoshPrice do produto PAI para chegar ao unit_price gravado. Só em mode = OPEN_ROSH. É margem, não custo — cost_price segue congelado normalmente.';

-- ---------------------------------------------------------------------------------------------
-- 2. Item do pedido — os dois atravessam o fechamento
-- ---------------------------------------------------------------------------------------------
-- Mesma razão que obrigou mode/courtesy a atravessarem na V114: sem eles no pedido, o histórico da
-- mesa não mostra o que foi lançado, e inferir pelo valor mentiria. A pergunta "qual pinça saiu com
-- aquela mesa" é feita DEPOIS de a mesa ter fechado — se a nota morresse na comanda, a tela de
-- Vendas > Pedidos não teria como respondê-la.
ALTER TABLE order_item ADD COLUMN notes            VARCHAR(200);
ALTER TABLE order_item ADD COLUMN surcharge_amount NUMERIC(14,2);

ALTER TABLE order_item ADD CONSTRAINT ck_order_item_surcharge_non_negative
    CHECK (surcharge_amount IS NULL OR surcharge_amount >= 0);

COMMENT ON COLUMN order_item.notes IS
    'Herdado da linha da comanda no fechamento (PDV-F011). Nulo em toda venda que não veio de mesa.';
COMMENT ON COLUMN order_item.surcharge_amount IS
    'Parcela de unit_price que veio de acréscimo manual no open rosh (PDV-F011). Nulo em toda venda que não veio de mesa.';
