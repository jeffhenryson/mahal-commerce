-- PDV-C015 — liquidação de pedido online no balcão passa a registrar o pagamento recebido.
--
-- O que falta no schema é só um estado: quando o cliente monta o pedido no app e vem pagar na
-- loja, a cobrança de gateway criada no checkout (ShopService.checkout grava
-- OrderPayment.pending(..., GATEWAY_PIX, ...)) nunca vai ser confirmada por webhook — ela precisa
-- ser ENCERRADA, e não deixada PENDING para sempre descrevendo uma cobrança que não vai acontecer.
--
-- Por que CANCELLED e não FAILED: FAILED é a cobrança que o gateway recusou, e é assim que ela
-- aparece em qualquer investigação de pagamento. Aqui nada falhou — a cobrança foi abandonada
-- porque o dinheiro entrou por outro caminho. Reaproveitar FAILED faria o relatório de falha de
-- gateway crescer com pagamentos que deram certo.
--
-- Não confundir com REFUNDED: estorno é dinheiro que entrou e voltou (linha nova, ledger
-- append-only, PDV-F007). CANCELLED é dinheiro que nunca entrou — a linha PENDING é atualizada no
-- lugar, mesma exceção documentada de OrderPayment.confirmCaptured, e pela mesma razão: não há
-- movimento de dinheiro para registrar, só o encerramento de uma cobrança em aberto.

ALTER TABLE order_payment DROP CONSTRAINT ck_order_payment_status;
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_status
    CHECK (status IN ('PENDING','AUTHORIZED','CAPTURED','REFUNDED','FAILED','CANCELLED'));
