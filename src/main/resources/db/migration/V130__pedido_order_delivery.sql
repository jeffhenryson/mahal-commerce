-- PDV-F022 — Entrega e retirada na venda de balcão.
--
-- Tabela própria, 1:1 com sales_order, só com linha para a venda que tem entrega: é a regra que o
-- Order documenta ("endereço e frete ficam em tabelas próprias, populadas só pelo canal que as
-- tem"), para não virar o pedido de 40 colunas em que metade é sempre nula. No JPA ela é a tabela
-- secundária de OrderEntity, lida por LEFT JOIN — sem consulta extra por pedido nas listagens.
--
-- ---------------------------------------------------------------------------------------------
-- Por que fee fica fora de net_amount
-- ---------------------------------------------------------------------------------------------
-- Mesma decisão da service_fee_amount (V118): net_amount é a receita da mercadoria, somada em
-- quatro agregações, e o frete é em boa parte repasse (motoboy, corrida da 99, postagem). O que o
-- cliente paga é net_amount + service_fee_amount + order_delivery.fee, e é contra esse total que o
-- pagamento é validado. O fechamento de caixa soma order_payment, então o dinheiro do frete passa
-- pela gaveta sem mudança nenhuma lá.

CREATE TABLE order_delivery (
    order_id      BIGINT        PRIMARY KEY REFERENCES sales_order (id) ON DELETE CASCADE,
    type          VARCHAR(20)   NOT NULL,
    method        VARCHAR(20),
    street        VARCHAR(200),
    number        VARCHAR(20),
    complement    VARCHAR(100),
    zip_code      VARCHAR(20),
    district      VARCHAR(100),
    city          VARCHAR(100),
    state         VARCHAR(50),
    country       VARCHAR(60),
    reference     VARCHAR(200),
    courier_name  VARCHAR(120),
    courier_phone VARCHAR(30),
    pickup_code   VARCHAR(60),
    dropoff_code  VARCHAR(60),
    tracking_code VARCHAR(60),
    fee           NUMERIC(14,2) NOT NULL DEFAULT 0,
    CONSTRAINT ck_order_delivery_type CHECK (type IN ('RETIRADA', 'ENTREGA')),
    CONSTRAINT ck_order_delivery_method CHECK (method IS NULL OR method IN ('MOTOBOY_LOJA', 'APP_99', 'CORREIOS')),
    CONSTRAINT ck_order_delivery_fee_non_negative CHECK (fee >= 0),
    -- Espelha o compact constructor de OrderDelivery: retirada não tem endereço, método nem taxa;
    -- entrega sempre tem endereço.
    CONSTRAINT ck_order_delivery_retirada_bare CHECK (
        type <> 'RETIRADA' OR (street IS NULL AND method IS NULL AND fee = 0)),
    CONSTRAINT ck_order_delivery_entrega_address CHECK (
        type <> 'ENTREGA' OR (street IS NOT NULL AND number IS NOT NULL AND city IS NOT NULL AND state IS NOT NULL))
);

COMMENT ON TABLE order_delivery IS
    'Entrega/retirada da venda de balcão (PDV-F022), 1:1 com sales_order e só para a venda que tem entrega. Códigos da 99 e rastreio dos Correios costumam chegar depois, via PATCH /orders/{id}/delivery.';
COMMENT ON COLUMN order_delivery.fee IS
    'Taxa de entrega cobrada do cliente, congelada na venda. FORA de net_amount (receita da mercadoria), como service_fee_amount: o que o cliente paga é net_amount + service_fee_amount + fee.';
