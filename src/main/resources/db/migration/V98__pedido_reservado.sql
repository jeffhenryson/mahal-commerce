-- PDV-F008 — RESERVADO: venda de balcão paga e baixada do estoque, aguardando o cliente retirar
-- depois. Alcançável só a partir de CRIADO (nunca de PAGO/AGUARDANDO_PAGAMENTO), o que restringe
-- a transição ao canal BALCAO por construção — ver o javadoc de OrderStatus.RESERVADO.

ALTER TABLE sales_order DROP CONSTRAINT ck_sales_order_status;
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_status
    CHECK (status IN ('CRIADO','AGUARDANDO_PAGAMENTO','PAGO','SEPARADO','ENVIADO','ENTREGUE',
                      'CONCLUIDO','CANCELADO','REEMBOLSADO','RESERVADO'));

-- Sem CHECK de coexistência com o status atual (diferente de cancelled_at/refunded_at): é
-- histórico, mesma régua de paid_at — permanece preenchido depois de RESERVADO -> CONCLUIDO.
ALTER TABLE sales_order ADD COLUMN reserved_at TIMESTAMP;

COMMENT ON COLUMN sales_order.reserved_at IS
    'Instante em que a venda de balcão foi marcada para retirada posterior (PDV-F008) — pagamento já capturado e estoque já baixado, mercadoria ainda não entregue. Permanece preenchido após a retirada (RESERVADO -> CONCLUIDO), histórico como paid_at.';
