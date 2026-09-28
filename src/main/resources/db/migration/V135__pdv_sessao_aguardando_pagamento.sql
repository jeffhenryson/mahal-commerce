-- PDV-F027 — sessões em paralelo na mesa, pagamento que leva ao preparo e "repetir sessão".
--
-- A sessão passa a nascer AGUARDANDO_PAGAMENTO (utensílio já reservado, tempo de mesa ainda não
-- começou); é o fechamento parcial que a cobra que a leva a PREPARANDO. vaso_grande guarda o que
-- antes só existia no texto de notes, para "repetir sessão" refazer a mesma configuração.

ALTER TABLE comanda_item DROP CONSTRAINT ck_comanda_item_session_status;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_session_status
    CHECK (session_status IS NULL
        OR session_status IN ('AGUARDANDO_PAGAMENTO','NA_FILA','PREPARANDO','ENTREGUE','RECOLHIDO'));

-- Fora da fila e já paga, o tempo de mesa já começou.
ALTER TABLE comanda_item DROP CONSTRAINT ck_comanda_item_session_started;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_session_started
    CHECK (session_status IS NULL OR session_status IN ('NA_FILA','AGUARDANDO_PAGAMENTO')
        OR started_at IS NOT NULL);

ALTER TABLE comanda_item ADD COLUMN vaso_grande BOOLEAN NOT NULL DEFAULT FALSE;
-- Backfill pelo sufixo que o ComandaService sempre gravou na nota da sessão de vaso grande.
UPDATE comanda_item SET vaso_grande = TRUE
 WHERE mode = 'SESSAO' AND notes LIKE '% · Vaso grande';
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_vaso_grande_by_mode
    CHECK (vaso_grande = FALSE OR mode = 'SESSAO');
