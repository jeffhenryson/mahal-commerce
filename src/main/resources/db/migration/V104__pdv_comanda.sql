-- PDV-F009 — Comanda de mesa: pedidos incrementais numa sessão de caixa aberta por horas (lounge
-- de narguilé), com fechamento único dividido entre formas de pagamento. Reaproveita
-- EstoqueUseCase.adjustStock item a item, exatamente como POST /pdv/sessions/{id}/sales já faz —
-- a diferença é que aqui a baixa acontece a cada item lançado, não numa única transação no fim.
--
-- Ao fechar, a comanda vira um Order comum (mesmo domínio de sales_order/order_item), então o
-- histórico de vendas, cashback e relatórios não precisam saber que aquele pedido nasceu de uma
-- comanda — só o registro da comanda (order_id) guarda esse rastro.

CREATE TABLE comanda (
    id                      BIGSERIAL     PRIMARY KEY,
    session_id              BIGINT        NOT NULL REFERENCES cash_register_session(id),
    warehouse_code          VARCHAR(50)   NOT NULL,
    table_or_customer_label VARCHAR(100)  NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    order_id                BIGINT        REFERENCES sales_order(id),
    opened_by               VARCHAR(80)   NOT NULL,
    opened_at               TIMESTAMP     NOT NULL,
    closed_at               TIMESTAMP,

    CONSTRAINT ck_comanda_status CHECK (status IN ('ABERTA','FECHADA','CANCELADA')),
    -- Espelha o compact constructor de Comanda: ABERTA não tem nem closed_at nem order_id;
    -- FECHADA tem os dois; CANCELADA tem closed_at mas nunca order_id (nunca virou pedido).
    CONSTRAINT ck_comanda_status_consistency CHECK (
        (status = 'ABERTA'    AND closed_at IS NULL     AND order_id IS NULL) OR
        (status = 'FECHADA'   AND closed_at IS NOT NULL AND order_id IS NOT NULL) OR
        (status = 'CANCELADA' AND closed_at IS NOT NULL AND order_id IS NULL)
    )
);

CREATE INDEX idx_comanda_session_status ON comanda (session_id, status);

COMMENT ON COLUMN comanda.warehouse_code IS
    'Depósito da sessão de caixa que abriu a comanda — mesma regra de cash_register_session.warehouse_code (PDV-C004), para não abrir brecha de baixar estoque de depósito alheio pela porta da comanda.';
COMMENT ON COLUMN comanda.order_id IS
    'Preenchido só no fechamento: o Order gerado a partir dos itens acumulados. Nulo em ABERTA e em CANCELADA — comanda cancelada nunca vira pedido.';

CREATE TABLE comanda_item (
    id           BIGSERIAL     PRIMARY KEY,
    comanda_id   BIGINT        NOT NULL REFERENCES comanda(id) ON DELETE CASCADE,
    sku          VARCHAR(50)   NOT NULL,
    quantity     NUMERIC(14,3) NOT NULL,
    unit_price   NUMERIC(14,2) NOT NULL,
    cost_price   NUMERIC(14,2),
    product_name VARCHAR(200),
    added_at     TIMESTAMP     NOT NULL,

    CONSTRAINT ck_comanda_item_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_comanda_item_comanda_id ON comanda_item (comanda_id);

COMMENT ON COLUMN comanda_item.unit_price IS
    'Preço congelado no instante em que o item foi lançado na comanda — mesma razão de order_item.unit_price: a comanda pode ficar aberta por horas, e o catálogo não pode reprecificar item já servido.';
COMMENT ON COLUMN comanda_item.cost_price IS
    'Custo congelado no lançamento, mesma razão de order_item.cost_price. Nulo quando o produto não tinha custo conhecido — nunca um default zero, que mentiria sobre a margem.';
