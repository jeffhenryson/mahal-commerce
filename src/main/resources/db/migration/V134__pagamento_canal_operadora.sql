-- PDV-F025 — canal e operadora do pagamento não-dinheiro.
--
-- O PDV pergunta "Maquininha do Balcão" ou "Link de Pagamento" e a operadora (Cielo/InfinityPay), e
-- até aqui descartava a resposta. Nulos em DINHEIRO e em todo pagamento anterior a esta migration.

ALTER TABLE order_payment ADD COLUMN channel  VARCHAR(20);
ALTER TABLE order_payment ADD COLUMN provider VARCHAR(20);

ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_channel
    CHECK (channel IS NULL OR channel IN ('MAQUININHA','LINK'));
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_provider
    CHECK (provider IS NULL OR provider IN ('CIELO','INFINITYPAY'));
-- Dinheiro não passa por maquininha nem link; operadora sem canal não diz por onde a cobrança saiu.
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_channel_not_cash
    CHECK (method <> 'DINHEIRO' OR (channel IS NULL AND provider IS NULL));
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_provider_needs_channel
    CHECK (provider IS NULL OR channel IS NOT NULL);
