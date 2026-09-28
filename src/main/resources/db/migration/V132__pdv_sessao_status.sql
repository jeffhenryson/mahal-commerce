-- PDV-F023 — status e tempo de mesa da sessão de narguilé.
--
-- A sessão passa a ser paga na hora do lançamento, então o pagamento deixou de dizer quando o
-- narguilé voltou para a casa. Quem diz é o status: NA_FILA (2º rosh do duplo esperando o 1º) →
-- PREPARANDO → ENTREGUE → RECOLHIDO. É o RECOLHIDO que libera os utensílios e deixa a mesa aceitar
-- outra sessão. Só linhas SESSAO/ROSH_EXTRA têm status; linha de catálogo fica nula.

ALTER TABLE comanda_item ADD COLUMN session_status VARCHAR(20);
ALTER TABLE comanda_item ADD COLUMN started_at     TIMESTAMP;
ALTER TABLE comanda_item ADD COLUMN delivered_at   TIMESTAMP;
ALTER TABLE comanda_item ADD COLUMN collected_at   TIMESTAMP;

-- Backfill, antes dos CHECKs:
--  * mesa já encerrada ou cancelada: a sessão acabou junto — RECOLHIDO, recolhida no fechamento.
--  * mesa ainda aberta: ENTREGUE, para o operador conseguir recolher pela tela. Deixar nulo a
--    tornaria invisível ao fluxo novo, e PREPARANDO mentiria sobre uma sessão que já está na mesa.
UPDATE comanda_item ci
   SET session_status = 'RECOLHIDO',
       started_at     = ci.added_at,
       collected_at   = COALESCE(c.closed_at, ci.added_at)
  FROM comanda c
 WHERE c.id = ci.comanda_id
   AND ci.mode IN ('SESSAO','ROSH_EXTRA')
   AND c.status <> 'ABERTA';

UPDATE comanda_item ci
   SET session_status = 'ENTREGUE',
       started_at     = ci.added_at,
       delivered_at   = ci.added_at
  FROM comanda c
 WHERE c.id = ci.comanda_id
   AND ci.mode IN ('SESSAO','ROSH_EXTRA')
   AND c.status = 'ABERTA';

ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_session_status
    CHECK (session_status IS NULL OR session_status IN ('NA_FILA','PREPARANDO','ENTREGUE','RECOLHIDO'));
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_session_status_by_mode
    CHECK (session_status IS NULL OR mode IN ('SESSAO','ROSH_EXTRA'));
-- Fora da fila o tempo de mesa já começou.
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_session_started
    CHECK (session_status IS NULL OR session_status = 'NA_FILA' OR started_at IS NOT NULL);
