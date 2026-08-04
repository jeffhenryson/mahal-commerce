-- ECM-F003 + ECM-C002 (Fatia 9) — carrinho do cliente do marketplace.
--
-- Substitui o stub descartado de core/domain/model/ecommerce/Cart.java (ECM-C002), que era um
-- record sem item nenhum. Numeração: o plano original estimava V72 para esta tabela, mas V72 já
-- foi consumida por order_refund_permission (Fatia 5) antes desta feature ser implementada — mesmo
-- padrão de drift já visto em V76 (estimada V71 no plano).

CREATE TABLE cart (
    id          BIGSERIAL   PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_customer UNIQUE (customer_id)
);

-- sku é texto livre, sem FK para product — mesma convenção de stock_balance/stock_movement/
-- stock_reservation: o carrinho não guarda preço (plano §2.9, resolvido do catálogo na exibição
-- e congelado só no checkout), e não há motivo para o carrinho ser mais rígido que o ledger de
-- estoque quanto a integridade referencial de SKU.
CREATE TABLE cart_item (
    id       BIGSERIAL      PRIMARY KEY,
    cart_id  BIGINT         NOT NULL REFERENCES cart(id) ON DELETE CASCADE,
    sku      VARCHAR(50)    NOT NULL,
    quantity NUMERIC(14,3)  NOT NULL,
    CONSTRAINT uk_cart_item_cart_sku UNIQUE (cart_id, sku),
    CONSTRAINT ck_cart_item_quantity_positive CHECK (quantity > 0)
);

COMMENT ON TABLE cart IS
    'Um carrinho por cliente (uk_cart_customer). Sem reserva de estoque — a reserva só acontece no checkout (plano §2.2): carrinho abandonado é a regra, não a exceção.';
