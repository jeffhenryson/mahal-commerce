-- PDV-F007 (Fatia 5) — REEMBOLSADO, distinto de CANCELADO: cancelar e reembolsar são eventos
-- diferentes, e contá-los juntos esconderia quanto dinheiro voltou de fato ao cliente. Levantado
-- com o dono em 2026-07-28, fechado como pedido no backlog (docs/dominios/vendas-balcao/README.md).
--
-- CANCELADO fica reservado a pedido cancelado ANTES de qualquer pagamento confirmado (nunca houve
-- dinheiro capturado nem baixa real de estoque para desfazer, só reserva). REEMBOLSADO é a única
-- saída para pedido com pagamento já confirmado — sempre há dinheiro e estoque real em jogo.

ALTER TABLE sales_order DROP CONSTRAINT ck_sales_order_status;
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_status
    CHECK (status IN ('CRIADO','AGUARDANDO_PAGAMENTO','PAGO','SEPARADO','ENVIADO','ENTREGUE',
                      'CONCLUIDO','CANCELADO','REEMBOLSADO'));

ALTER TABLE sales_order ADD COLUMN refunded_at TIMESTAMP;

-- Mesma invariante de cancelled_at, espelhando o compact constructor de Order.
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_refunded_consistency
    CHECK ((status = 'REEMBOLSADO') = (refunded_at IS NOT NULL));

COMMENT ON COLUMN sales_order.cancel_reason IS
    'Motivo do encerramento — cancelamento OU reembolso, nunca os dois (status é exclusivo entre CANCELADO e REEMBOLSADO). Um só campo evita duplicar a mesma informação sob dois nomes.';
COMMENT ON COLUMN sales_order.refunded_at IS
    'Instante do reembolso (PDV-F007) — pagamento estornado, cashback revertido e mercadoria devolvida ao estoque, tudo na mesma transação. Distinto de cancelled_at: um pedido termina em CANCELADO ou REEMBOLSADO, nunca os dois.';
