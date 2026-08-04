-- Defesa em profundidade para a idempotência de CashbackService.recordEarnedForOrder
-- (achado real de CashbackLedgerConcurrencyIT, 2026-08-04): o lock em memória por orderId no
-- service já impede duplicação dentro de UMA instância da aplicação; este índice único parcial
-- cobre o caso que o lock não cobre — múltiplas instâncias/processos, mesmo raciocínio já usado
-- para uk_order_payment_gateway_ref (V68).
--
-- Parcial (WHERE type = 'EARNED'), não um UNIQUE geral em (order_id, order_item_id): entradas
-- REVERSED/EXPIRED referenciam de propósito o mesmo order_id/order_item_id do EARNED original
-- (é assim que reversesEntryId aponta de volta) — um índice não-parcial rejeitaria essas linhas
-- companheiras legítimas.
--
-- Mesma lacuna já aceita do projeto para índice único parcial (ex.:
-- uk_cash_register_session_open_operator): o perfil dev usa ddl-auto=create-drop (schema gerado
-- pelo Hibernate a partir das entities, sem Flyway) — este índice só existe de fato em hml/prod.
-- CashbackLedgerConcurrencyIT roda em dev/H2 e por isso depende inteiramente do lock em memória
-- do service, não deste índice, para passar.
CREATE UNIQUE INDEX uk_cashback_entry_earned_per_order_item
    ON cashback_entry (order_id, order_item_id)
    WHERE type = 'EARNED';
