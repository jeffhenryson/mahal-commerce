-- PDV-F003/F004/F005 — Fundação do pedido: cash_register_sale vira sales_order.
--
-- Venda de balcão e pedido de marketplace passam a ser a MESMA entidade, discriminada por canal.
-- Tudo que vem depois — extrato do cliente, ledger de cashback, devolução, faturamento, documento
-- fiscal, relatório de margem — consulta "vendas, independente de canal". Com duas tabelas, cada
-- um desses consumidores pagaria um UNION ou duplicaria lógica; com uma, paga um WHERE channel = ?
-- quando precisa distinguir, e nada quando não precisa.
--
-- Esta migration acontece AGORA, com volume quase zero, porque é a única mudança do projeto cujo
-- custo cresce a cada venda gravada: pedido sem cost_price é pedido cuja margem real não é mais
-- recuperável, e o custo antigo não fica em lugar nenhum.

-- ---------------------------------------------------------------------------------------------
-- Renomeação
-- ---------------------------------------------------------------------------------------------
ALTER TABLE cash_register_sale RENAME TO sales_order;
ALTER TABLE sale_item          RENAME TO order_item;
ALTER TABLE order_item RENAME COLUMN sale_id TO order_id;

ALTER INDEX idx_cash_register_sale_session_id RENAME TO idx_sales_order_session_id;
ALTER INDEX idx_sale_item_sale_id             RENAME TO idx_order_item_order_id;

-- ---------------------------------------------------------------------------------------------
-- Cabeçalho do pedido
-- ---------------------------------------------------------------------------------------------
ALTER TABLE sales_order ADD COLUMN channel           VARCHAR(20);
ALTER TABLE sales_order ADD COLUMN status            VARCHAR(30);
ALTER TABLE sales_order ADD COLUMN order_number      VARCHAR(30);
ALTER TABLE sales_order ADD COLUMN customer_id       BIGINT REFERENCES customers(id);
ALTER TABLE sales_order ADD COLUMN discount_amount   NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE sales_order ADD COLUMN cashback_redeemed NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE sales_order ADD COLUMN net_amount        NUMERIC(14,2);
ALTER TABLE sales_order ADD COLUMN change_amount     NUMERIC(14,2);
ALTER TABLE sales_order ADD COLUMN cancel_reason     VARCHAR(255);
ALTER TABLE sales_order ADD COLUMN paid_at           TIMESTAMP;
ALTER TABLE sales_order ADD COLUMN concluded_at      TIMESTAMP;
ALTER TABLE sales_order ADD COLUMN cancelled_at      TIMESTAMP;
ALTER TABLE sales_order ADD COLUMN version           BIGINT NOT NULL DEFAULT 0;

-- Marketplace não tem caixa. A obrigatoriedade por canal fica no CHECK, mais abaixo.
ALTER TABLE sales_order ALTER COLUMN session_id DROP NOT NULL;

-- Backfill dos pedidos legados: toda venda existente é de balcão e já terminou.
-- A numeração legada é prefixada para nunca colidir com a sequência nova, e para ficar óbvio em
-- qualquer relatório que aquele número não saiu do contador fiscal.
UPDATE sales_order SET
    channel      = 'BALCAO',
    status       = 'CONCLUIDO',
    net_amount   = total_amount,
    concluded_at = sold_at,
    paid_at      = sold_at,
    order_number = 'LEG-' || LPAD(id::text, 8, '0')
WHERE channel IS NULL;

ALTER TABLE sales_order ALTER COLUMN channel      SET NOT NULL;
ALTER TABLE sales_order ALTER COLUMN status       SET NOT NULL;
ALTER TABLE sales_order ALTER COLUMN net_amount   SET NOT NULL;
ALTER TABLE sales_order ALTER COLUMN order_number SET NOT NULL;

ALTER TABLE sales_order ADD CONSTRAINT uk_sales_order_number UNIQUE (order_number);

ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_channel
    CHECK (channel IN ('BALCAO','MARKETPLACE'));
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_status
    CHECK (status IN ('CRIADO','AGUARDANDO_PAGAMENTO','PAGO','SEPARADO','ENVIADO','ENTREGUE',
                      'CONCLUIDO','CANCELADO'));

-- Espelham as invariantes do compact constructor de Order. Redundante por desenho: o domínio é a
-- primeira barreira, o schema é a que sobrevive a carga direta e script de correção.
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_customer_by_channel
    CHECK (channel = 'BALCAO' OR customer_id IS NOT NULL);
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_session_by_channel
    CHECK ((channel = 'BALCAO'      AND session_id IS NOT NULL)
        OR (channel = 'MARKETPLACE' AND session_id IS NULL));
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_amounts_non_negative
    CHECK (total_amount >= 0 AND discount_amount >= 0
       AND cashback_redeemed >= 0 AND net_amount >= 0);
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_net_amount
    CHECK (net_amount = total_amount - discount_amount - cashback_redeemed);
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_cancelled_consistency
    CHECK ((status = 'CANCELADO') = (cancelled_at IS NOT NULL));
-- Troco só existe onde há dinheiro em espécie mudando de mão.
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_change_only_balcao
    CHECK (change_amount IS NULL OR change_amount = 0 OR channel = 'BALCAO');

CREATE INDEX idx_sales_order_customer_id     ON sales_order (customer_id);
CREATE INDEX idx_sales_order_channel_status  ON sales_order (channel, status);
CREATE INDEX idx_sales_order_concluded_at    ON sales_order (concluded_at DESC);

COMMENT ON COLUMN sales_order.customer_id IS
    'Nulo em BALCAO: a venda anônima de passagem é o caso normal. O CHECK amarra o marketplace, onde pedido sem cliente não tem para quem entregar nem para quem estornar.';
COMMENT ON COLUMN sales_order.order_number IS
    'Numeração de sequência PRÓPRIA, emitida na CONCLUSÃO. Não deriva do id: BIGSERIAL deixa buracos em rollback de transação, e buraco em numeração de documento fiscal é problema com o fisco. Pedidos anteriores a esta migration têm prefixo LEG-.';
COMMENT ON COLUMN sales_order.total_amount IS
    'Bruto do pedido: soma de quantity * unit_price dos itens, antes de desconto e resgate de cashback.';
COMMENT ON COLUMN sales_order.change_amount IS
    'Troco. NÃO é linha de pagamento: modelar troco como pagamento negativo faria todo SUM(amount) mentir.';

-- ---------------------------------------------------------------------------------------------
-- Item do pedido: os três valores congelados
-- ---------------------------------------------------------------------------------------------
ALTER TABLE order_item ADD COLUMN cost_price       NUMERIC(14,2);
ALTER TABLE order_item ADD COLUMN discount_amount  NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE order_item ADD COLUMN cashback_percent NUMERIC(9,4);

ALTER TABLE order_item ADD CONSTRAINT ck_order_item_quantity_positive
    CHECK (quantity > 0);
ALTER TABLE order_item ADD CONSTRAINT ck_order_item_discount_within_gross
    CHECK (discount_amount >= 0 AND discount_amount <= quantity * unit_price);
ALTER TABLE order_item ADD CONSTRAINT ck_order_item_cashback_percent_range
    CHECK (cashback_percent IS NULL OR (cashback_percent >= 0 AND cashback_percent <= 100));

-- cost_price e cashback_percent ficam NULOS nos itens legados de propósito. Um DEFAULT 0 mentiria
-- sobre a margem e sobre o cashback gerado — mesma razão pela qual V63 não deu default a
-- product.cost_price. Nulo diz "não se sabe", que é a verdade.
COMMENT ON COLUMN order_item.cost_price IS
    'Snapshot do custo no instante da venda. Sem ele, a próxima compra que alterar o custo do produto reescreve a margem histórica de TODOS os pedidos passados, e não há como reconstruir. Nulo só em itens anteriores a esta migration.';
COMMENT ON COLUMN order_item.cashback_percent IS
    'Taxa vigente carimbada na venda. Sem o snapshot, mudar a taxa amanhã reescreveria o valor gerado por pedidos de ontem. Escala 4 porque é input de fórmula, não valor de exibição.';
COMMENT ON COLUMN order_item.discount_amount IS
    'Desconto concedido no item. Campo próprio em vez de unit_price menor: é o que permite responder "quanto de margem o desconto do balcão comeu no mês" com um SUM, em vez de com arqueologia.';

-- ---------------------------------------------------------------------------------------------
-- Numeração fiscal
-- ---------------------------------------------------------------------------------------------
-- Sequência dedicada, e não o id da tabela, para que a numeração não tenha buracos por rollback.
-- Começa acima do volume legado por folga, para deixar claro onde a numeração nova começou.
CREATE SEQUENCE order_number_seq START 1000;

-- ---------------------------------------------------------------------------------------------
-- RBAC
-- ---------------------------------------------------------------------------------------------
-- Desconto é permissão separada de vender: quem opera o caixa registra venda, mas conceder abatimento
-- é decisão comercial. O teto percentual fica em configuração, não em uma segunda permissão — dois
-- níveis de permissão só criariam a tentação de distribuir o nível maior.
INSERT INTO permissions (name) VALUES ('PDV_SALE_DISCOUNT')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'PDV_SALE_DISCOUNT'
ON CONFLICT DO NOTHING;
