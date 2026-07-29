-- PDV-F006 — Pagamento de balcão: uma tabela, dois fluxos (dinheiro na gaveta hoje, gateway na
-- Fatia 10 amanhã). Ver docs/plano-pdv-marketplace.md §2.6.
--
-- Balcão grava já em CAPTURED — o dinheiro está na gaveta no instante da venda. Múltiplas linhas
-- por pedido = pagamento dividido (ex.: R$50 dinheiro + R$30 débito).

CREATE TABLE order_payment (
    id            BIGSERIAL     PRIMARY KEY,
    order_id      BIGINT        NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
    method        VARCHAR(30)   NOT NULL,
    amount        NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    status        VARCHAR(20)   NOT NULL,
    installments  INT,
    gateway_ref   VARCHAR(120),
    authorized_at TIMESTAMP,
    captured_at   TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL,

    CONSTRAINT ck_order_payment_method CHECK (method IN ('DINHEIRO','DEBITO','CREDITO','PIX')),
    CONSTRAINT ck_order_payment_status CHECK (status IN ('PENDING','AUTHORIZED','CAPTURED','REFUNDED','FAILED')),
    CONSTRAINT ck_order_payment_captured CHECK ((status = 'CAPTURED') = (captured_at IS NOT NULL)),
    -- Parcelamento só faz sentido em cartão de crédito. Teto de 24x é generoso o bastante para
    -- não ser um limite de negócio disfarçado de constraint técnica.
    CONSTRAINT ck_order_payment_installments
        CHECK (installments IS NULL OR (method = 'CREDITO' AND installments BETWEEN 1 AND 24))
);

CREATE INDEX idx_order_payment_order_id ON order_payment (order_id);

-- Idempotência do webhook (Fatia 10): o gateway reenvia, e reenvio não pode virar segundo
-- pagamento. Índice único desde agora, muito antes do gateway existir — é o que o risco #10 do
-- plano pede: "muito antes do gateway existir".
CREATE UNIQUE INDEX uk_order_payment_gateway_ref
    ON order_payment (gateway_ref) WHERE gateway_ref IS NOT NULL;

COMMENT ON COLUMN order_payment.gateway_ref IS
    'Referência do gateway externo. Nula em pagamento de balcão (dinheiro/débito/crédito/PIX manual) — existe para a Fatia 10, quando o pagamento online passar a chegar por webhook.';
COMMENT ON COLUMN order_payment.installments IS
    'Número de parcelas no crédito. Nulo nos demais métodos. Guardado para conciliar o repasse parcelado da adquirente — a maquininha por si só não precisa disso do backend.';
