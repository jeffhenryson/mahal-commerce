-- PDV-F010 — Canal MESA no pedido: o fechamento de comanda passa a gerar um pedido que se declara
-- consumo de salão, com a comanda e o rótulo da mesa junto.
--
-- ATENÇÃO, é o coração desta migration: a V65 gravou QUATRO invariantes de canal no schema, e três
-- delas rejeitam um pedido de mesa. Adicionar o valor ao enum sem relaxá-las faria todo fechamento
-- de comanda estourar CHECK no INSERT — e só em produção, porque o teste de domínio passaria.
-- As mesmas regras estão duplicadas no compact constructor de Order (por desenho: "o domínio é a
-- primeira barreira, o schema é a que sobrevive a carga direta"), e os dois lados mudam juntos.

-- ---------------------------------------------------------------------------------------------
-- 1. Colunas de origem da mesa
-- ---------------------------------------------------------------------------------------------
ALTER TABLE sales_order ADD COLUMN comanda_id  BIGINT REFERENCES comanda(id);
ALTER TABLE sales_order ADD COLUMN table_label VARCHAR(100);
CREATE INDEX idx_sales_order_comanda_id ON sales_order (comanda_id);

COMMENT ON COLUMN sales_order.comanda_id IS
    'Comanda que originou o pedido. Redundante com comanda.order_id, que aponta de volta — os dois são gravados na MESMA transação de closeComanda. A redundância é deliberada: sem esta coluna, listar pedidos de mesa com o rótulo exigiria join reverso em toda página de Vendas > Pedidos.';
COMMENT ON COLUMN sales_order.table_label IS
    'Rótulo da mesa congelado no fechamento (ex.: "Mesa 4"). Congelado, e não lido da comanda, pela mesma razão de order_item.product_name: renomear a mesa depois não pode reescrever o histórico.';

-- ---------------------------------------------------------------------------------------------
-- 2. As quatro CHECKs de canal da V65
-- ---------------------------------------------------------------------------------------------

-- (a) O enum. Sem isto, nada mais importa.
ALTER TABLE sales_order DROP CONSTRAINT ck_sales_order_channel;
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_channel
    CHECK (channel IN ('BALCAO','MESA','MARKETPLACE'));

-- (b) Cliente obrigatório. A regra da V65 era "todo canal menos BALCAO exige cliente"; MESA entra
--     na exceção junto com o balcão, porque a mesa pode ser aberta sem vínculo de cadastro — é o
--     caso normal do salão. Marketplace continua exigindo: pedido online sem cliente não tem para
--     quem entregar nem para quem estornar.
ALTER TABLE sales_order DROP CONSTRAINT ck_sales_order_customer_by_channel;
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_customer_by_channel
    CHECK (channel IN ('BALCAO','MESA') OR customer_id IS NOT NULL);

-- (c) Sessão de caixa obrigatória. A V65 amarrava só o balcão. MESA entra junto — a comanda nasce
--     dentro de uma sessão aberta, e um pedido de mesa sem caixa não teria gaveta para conferir.
--     Marketplace continua livre (PODE ter sessão: pedido do app pago na loja).
ALTER TABLE sales_order DROP CONSTRAINT ck_sales_order_session_by_channel;
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_session_by_channel
    CHECK (channel NOT IN ('BALCAO','MESA') OR session_id IS NOT NULL);

-- (d) Troco. A mesa fecha pela MESMA validatePaymentsAndComputeChange da venda de balcão, então
--     fechamento em dinheiro gera troco igual. Manter a regra como estava barraria toda mesa paga
--     em espécie com valor tendido a mais.
ALTER TABLE sales_order DROP CONSTRAINT ck_sales_order_change_only_balcao;
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_change_amount_by_channel
    CHECK (change_amount IS NULL OR change_amount = 0 OR channel IN ('BALCAO','MESA'));

-- (e) Nova: comandaId/tableLabel só existem no canal MESA, e são obrigatórios nele. Espelha o
--     compact constructor de Order.
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_mesa_origin
    CHECK ((channel = 'MESA' AND comanda_id IS NOT NULL AND table_label IS NOT NULL)
        OR (channel <> 'MESA' AND comanda_id IS NULL AND table_label IS NULL));

-- ---------------------------------------------------------------------------------------------
-- 3. Backfill — decisão consciente, não default acidental
-- ---------------------------------------------------------------------------------------------
-- Todo pedido já gerado por fechamento de comanda está gravado como BALCAO, porque era o único
-- canal presencial que existia. Reclassificamos: sem isto o filtro "Mesa" de Vendas > Pedidos não
-- mostraria nada de antes desta entrega, e a análise por canal ficaria com uma quebra na série.
--
-- É a ÚNICA vez que o canal — documentado como imutável — é reescrito. A imutabilidade vale para a
-- aplicação; uma migration corrigindo classificação histórica é a exceção que a V65 já abriu
-- quando backfillou todo pedido legado como BALCAO/CONCLUIDO.
--
-- A ordem importa: o UPDATE precisa preencher comanda_id/table_label JUNTO com o canal, ou a
-- CHECK ck_sales_order_mesa_origin recém-criada rejeita a própria linha que estamos corrigindo.
UPDATE sales_order o
SET channel     = 'MESA',
    comanda_id  = c.id,
    table_label = c.table_or_customer_label
FROM comanda c
WHERE c.order_id = o.id
  AND o.channel = 'BALCAO';
