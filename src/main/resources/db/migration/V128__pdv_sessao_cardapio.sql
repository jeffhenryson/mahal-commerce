-- PDV-F021 — Cardápio de sessão da mesa: a sessão de narguilé deixa de ser um Produto do catálogo.
--
-- Até aqui a sessão era um produto com flags (session_product, open_rosh_price, V112) e o preço saía
-- da variação da essência — o que misturava estoque de lata com o cardápio do salão. A casa vende a
-- sessão por FAIXA de preço, escolhida pela marca da essência:
--   Zgy, Zomo, Pred → R$ 25 · Luk, Smynar, Nay → R$ 30 · Sence → R$ 40
-- A sessão inclui vaso pequeno, pinça, prato e tapete; vaso grande é upgrade de R$ 10. Em dias de
-- promoção "duplo rosh" o segundo rosh (nova essência, mesmos utensílios) sai de graça.
--
-- Decisões:
--  * Faixa e utensílio são cadastros próprios (session_tier, session_asset_type), editáveis no admin.
--  * A essência é texto livre na linha (comanda_item.notes) — sem baixa de estoque.
--  * A linha da comanda continua sendo comanda_item, com SKU sintético 'SESS-{tier_id}' e os modos
--    novos SESSAO / ROSH_EXTRA: fechamento, conta dividida, desconto, taxa e cashback seguem iguais.
--    Não há FK de sku em comanda_item/order_item, então o SKU sintético não quebra nada.
--  * Utensílio é ATIVO da casa, não estoque: a sessão ALOCA um de cada ao ser lançada e LIBERA ao
--    fechar, cancelar ou remover. Disponível = quantidade_total − alocações sem liberado_em.

CREATE TABLE session_tier (
    id         BIGSERIAL     PRIMARY KEY,
    nome       VARCHAR(60)   NOT NULL,
    preco      NUMERIC(12,2) NOT NULL,
    marcas     VARCHAR(255),
    ordem      INTEGER       NOT NULL DEFAULT 0,
    ativo      BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_session_tier_preco CHECK (preco >= 0)
);
CREATE UNIQUE INDEX uk_session_tier_nome_lower ON session_tier (LOWER(nome));

COMMENT ON COLUMN session_tier.marcas IS
    'Marcas de essência atendidas pela faixa, texto livre para a tela (ex.: "Zgy, Zomo, Pred").';

CREATE TABLE session_asset_type (
    id               BIGSERIAL   PRIMARY KEY,
    codigo           VARCHAR(30) NOT NULL,
    nome             VARCHAR(60) NOT NULL,
    quantidade_total INTEGER     NOT NULL DEFAULT 0,
    incluso          BOOLEAN     NOT NULL DEFAULT FALSE,
    ativo            BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_session_asset_type_codigo UNIQUE (codigo),
    CONSTRAINT ck_session_asset_type_quantidade CHECK (quantidade_total >= 0)
);

COMMENT ON COLUMN session_asset_type.incluso IS
    'Acompanha TODA sessão (pinça, prato, tapete). O vaso não é incluso: vem de session_settings, '
    'porque a sessão leva o vaso padrão OU o grande.';

-- Linha única (id = 1): vaso padrão, vaso grande, preço do upgrade e dias do duplo rosh.
CREATE TABLE session_settings (
    id                        SMALLINT      PRIMARY KEY DEFAULT 1,
    vaso_padrao_codigo        VARCHAR(30),
    vaso_grande_codigo        VARCHAR(30),
    upgrade_vaso_grande_preco NUMERIC(12,2) NOT NULL DEFAULT 0,
    dias_duplo_rosh           VARCHAR(80)   NOT NULL DEFAULT '',
    CONSTRAINT ck_session_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_session_settings_upgrade CHECK (upgrade_vaso_grande_preco >= 0)
);

COMMENT ON COLUMN session_settings.dias_duplo_rosh IS
    'Dias da semana (java.time.DayOfWeek, separados por vírgula) em que o 2º rosh sai a R$ 0.';

-- Alocação de utensílio por linha de sessão. ON DELETE SET NULL: a remoção da linha libera
-- explicitamente antes; o registro fica para o histórico de uso.
CREATE TABLE comanda_session_asset (
    id              BIGSERIAL PRIMARY KEY,
    comanda_item_id BIGINT    REFERENCES comanda_item (id) ON DELETE SET NULL,
    asset_type_id   BIGINT    NOT NULL REFERENCES session_asset_type (id),
    quantidade      INTEGER   NOT NULL DEFAULT 1,
    alocado_em      TIMESTAMP NOT NULL,
    liberado_em     TIMESTAMP,
    CONSTRAINT ck_comanda_session_asset_quantidade CHECK (quantidade > 0)
);
CREATE INDEX idx_comanda_session_asset_item ON comanda_session_asset (comanda_item_id);
CREATE INDEX idx_comanda_session_asset_em_uso ON comanda_session_asset (asset_type_id)
    WHERE liberado_em IS NULL;

-- Modos novos da linha: SESSAO (a sessão do cardápio) e ROSH_EXTRA (o 2º rosh, ligado à sessão).
ALTER TABLE comanda_item DROP CONSTRAINT ck_comanda_item_mode;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_mode
    CHECK (mode IN ('NORMAL','OPEN_ROSH','SABOR_EXTRA','TROCA','SESSAO','ROSH_EXTRA'));
ALTER TABLE comanda_item DROP CONSTRAINT ck_comanda_item_linked_by_mode;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_linked_by_mode
    CHECK (linked_item_id IS NULL OR mode IN ('SABOR_EXTRA','TROCA','ROSH_EXTRA'));
ALTER TABLE order_item DROP CONSTRAINT ck_order_item_mode;
ALTER TABLE order_item ADD CONSTRAINT ck_order_item_mode
    CHECK (mode IN ('NORMAL','OPEN_ROSH','SABOR_EXTRA','TROCA','SESSAO','ROSH_EXTRA'));

-- Seeds do cardápio atual da casa. Quantidades de utensílio começam em 0: o admin informa quantos
-- tem de cada antes de lançar a primeira sessão (sem isso a sessão é recusada por falta de vaso).
INSERT INTO session_tier (nome, preco, marcas, ordem) VALUES
    ('Tradicional', 25.00, 'Zgy, Zomo, Pred', 1),
    ('Premium',     30.00, 'Luk, Smynar, Nay', 2),
    ('Sence',       40.00, 'Sence', 3);

INSERT INTO session_asset_type (codigo, nome, quantidade_total, incluso) VALUES
    ('VASO_P', 'Vaso pequeno', 0, FALSE),
    ('VASO_G', 'Vaso grande',  0, FALSE),
    ('PINCA',  'Pinça',        0, TRUE),
    ('PRATO',  'Prato',        0, TRUE),
    ('TAPETE', 'Tapete',       0, TRUE);

INSERT INTO session_settings (id, vaso_padrao_codigo, vaso_grande_codigo, upgrade_vaso_grande_preco, dias_duplo_rosh)
VALUES (1, 'VASO_P', 'VASO_G', 10.00, '');

-- Permissão de cadastro do cardápio (faixas, utensílios, configuração) — só admin.
INSERT INTO permissions (name) VALUES ('PDV_SESSAO_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'PDV_SESSAO_MANAGE'
ON CONFLICT DO NOTHING;
