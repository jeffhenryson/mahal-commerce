-- PDV-F024 — carvão e adicionais pagos da sessão de narguilé.
--
-- Carvão (CUBO/JUMBO) é só registro, sem efeito em preço: vai na linha da comanda e atravessa para
-- a linha do pedido, igual a notes. Adicional (filtro de gelo) é cadastro próprio, fora do
-- catálogo, no molde de session_tier (V128); o que a linha cobrou fica em comanda_item_addon como
-- snapshot, para mudar o preço amanhã não reescrever a conta de hoje.

ALTER TABLE comanda_item ADD COLUMN charcoal VARCHAR(10);
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_charcoal
    CHECK (charcoal IS NULL OR charcoal IN ('CUBO','JUMBO'));
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_charcoal_by_mode
    CHECK (charcoal IS NULL OR mode IN ('SESSAO','ROSH_EXTRA'));

ALTER TABLE order_item ADD COLUMN charcoal VARCHAR(10);
ALTER TABLE order_item ADD CONSTRAINT ck_order_item_charcoal
    CHECK (charcoal IS NULL OR charcoal IN ('CUBO','JUMBO'));

CREATE TABLE session_addon (
    id     BIGSERIAL     PRIMARY KEY,
    nome   VARCHAR(60)   NOT NULL,
    preco  NUMERIC(12,2) NOT NULL,
    ordem  INTEGER       NOT NULL DEFAULT 0,
    ativo  BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_session_addon_preco CHECK (preco >= 0)
);
CREATE UNIQUE INDEX uk_session_addon_nome_lower ON session_addon (LOWER(nome));
INSERT INTO session_addon (nome, preco, ordem) VALUES ('Filtro de gelo', 5.00, 1);

-- addon_id sem FK: o cadastro pode ser editado, e o snapshot (nome, preco) é a verdade da linha.
CREATE TABLE comanda_item_addon (
    id              BIGSERIAL     PRIMARY KEY,
    comanda_item_id BIGINT        NOT NULL REFERENCES comanda_item (id) ON DELETE CASCADE,
    addon_id        BIGINT,
    nome            VARCHAR(60)   NOT NULL,
    preco           NUMERIC(12,2) NOT NULL,
    CONSTRAINT ck_comanda_item_addon_preco CHECK (preco >= 0)
);
CREATE INDEX idx_comanda_item_addon_item ON comanda_item_addon (comanda_item_id);
