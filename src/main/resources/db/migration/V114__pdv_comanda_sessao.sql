-- PDV-F010 — Cliente na comanda, e o modo/cortesia de cada linha.
--
-- O problema que isto resolve: hoje a comanda é uma lista plana de {sku, quantity} com o preço
-- resolvido no servidor. Não existe onde dizer POR QUE a linha existe (é a sessão? o segundo sabor
-- de um duplo? uma troca durante um open rosh?) nem QUE ELA É CORTESIA. Sem isso, a promo
-- "pague 1 leve 2" e a troca de sabor só existiriam na cabeça do atendente.

-- ---------------------------------------------------------------------------------------------
-- 1. Cliente da mesa
-- ---------------------------------------------------------------------------------------------
-- Distinto de table_or_customer_label, que é texto livre para achar a mesa na tela ("Mesa 4", "o
-- rapaz de boné") e sempre foi documentado como "não é um vínculo de cadastro". É este customer_id
-- que faz o pedido da mesa sair com nome e gerar cashback, como o balcão já faz.
ALTER TABLE comanda ADD COLUMN customer_id BIGINT REFERENCES customers(id);
CREATE INDEX idx_comanda_customer_id ON comanda (customer_id);

COMMENT ON COLUMN comanda.customer_id IS
    'Cliente do CRM vinculado na abertura da mesa, opcional. Não confundir com table_or_customer_label, que é rótulo de tela e nunca foi vínculo de cadastro.';

-- ---------------------------------------------------------------------------------------------
-- 2. Modo e cortesia na linha da comanda
-- ---------------------------------------------------------------------------------------------
-- DEFAULT 'NORMAL'/FALSE preserva o comportamento de toda linha já lançada: antes desta feature
-- toda linha era um item comum cobrado.
ALTER TABLE comanda_item ADD COLUMN mode           VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE comanda_item ADD COLUMN courtesy       BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE comanda_item ADD COLUMN linked_item_id BIGINT      REFERENCES comanda_item(id);

ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_mode
    CHECK (mode IN ('NORMAL','OPEN_ROSH','SABOR_EXTRA','TROCA'));

-- Cortesia é preço zero por definição. A recíproca NÃO vale, e é justamente por isso que a coluna
-- existe em vez de ser inferida: um item pode custar zero sem ser cortesia, e um desconto de 100%
-- dá o mesmo zero de uma cortesia.
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_courtesy_is_free
    CHECK (courtesy = FALSE OR unit_price = 0);

-- linked_item_id só faz sentido nos modos que existem "pendurados" em outra linha.
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_linked_by_mode
    CHECK (linked_item_id IS NULL OR mode IN ('SABOR_EXTRA','TROCA'));

CREATE INDEX idx_comanda_item_linked_item_id ON comanda_item (linked_item_id);

COMMENT ON COLUMN comanda_item.mode IS
    'Por que a linha existe: NORMAL (item comum ou a sessão em si), OPEN_ROSH (consumo livre por valor fixo), SABOR_EXTRA (segundo sabor do duplo), TROCA (troca de sabor durante um open rosh).';
COMMENT ON COLUMN comanda_item.courtesy IS
    'Linha a preço zero que AINDA baixa estoque. Campo próprio, não inferido de unit_price = 0: um desconto de 100% dá o mesmo zero, e a margem precisa distinguir os dois.';
COMMENT ON COLUMN comanda_item.linked_item_id IS
    'Linha de origem, na mesma comanda: a sessão que este segundo sabor acompanha, ou o open rosh que esta troca pertence.';

-- ---------------------------------------------------------------------------------------------
-- 3. O mesmo par no item do PEDIDO
-- ---------------------------------------------------------------------------------------------
-- Sem isto o histórico da mesa em Vendas > Pedidos não distingue cortesia de item cobrado. Não há
-- linked_item_id aqui de propósito: o vínculo entre linhas é operação de comanda aberta (decide se
-- a tela mostra "Trocar sabor"), e no pedido fechado ele não responde nenhuma pergunta.
ALTER TABLE order_item ADD COLUMN mode     VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE order_item ADD COLUMN courtesy BOOLEAN     NOT NULL DEFAULT FALSE;

ALTER TABLE order_item ADD CONSTRAINT ck_order_item_mode
    CHECK (mode IN ('NORMAL','OPEN_ROSH','SABOR_EXTRA','TROCA'));

COMMENT ON COLUMN order_item.mode IS
    'Modo da linha, herdado do comanda_item no fechamento. NORMAL em toda venda que não veio de mesa.';
COMMENT ON COLUMN order_item.courtesy IS
    'Linha cortesia: net_amount zero com cost_price congelado normalmente. É o que faz a margem do pedido mostrar o prejuízo real da promo e do open rosh.';
