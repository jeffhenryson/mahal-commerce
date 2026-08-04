-- ECM-F004 (Fatia 10) — gateway de pagamento InfinitePay. Só amplia o CHECK de método: o resto
-- do schema já foi deixado pronto para esta feature desde a V68 (gateway_ref nulável com índice
-- único parcial, status PENDING/AUTHORIZED/CAPTURED/REFUNDED/FAILED já no CHECK de status).
--
-- Pivot registrado aqui: plano-pdv-marketplace.md §2.6 supunha Mercado Pago; o gateway escolhido
-- foi InfinitePay. Não muda nada de schema — só o valor novo do enum de método.

ALTER TABLE order_payment DROP CONSTRAINT ck_order_payment_method;
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_method
    CHECK (method IN ('DINHEIRO','DEBITO','CREDITO','PIX','GATEWAY_PIX'));
