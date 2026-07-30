-- CRM-F003 — Programa de cashback: ganhar cashback (taxa por abrangência, ledger append-only,
-- lançamento na conclusão da venda, expiração automática). Resgate no balcão (CASHBACK_REDEEM)
-- e ajuste manual (CASHBACK_ADJUST) ficam para uma fatia seguinte, isolada — ver
-- docs/plano-pdv-marketplace.md §2.4 e docs/dominios/crm/proximos-passos.md.
--
-- Numeração: V69 já foi consumida por crm_customer_identifiers, no mesmo dia.

-- ---------------------------------------------------------------------------------------------
-- Taxa por abrangência: SKU → CATEGORY → GLOBAL, a regra ativa e vigente mais específica vence.
-- Uma tabela uniforme para as três abrangências, em vez de uma coluna cashback_percent em
-- product, porque product.category é texto livre sem tabela própria, e taxa ZERO é significativa
-- (diferente de "sem taxa definida") — por isso abrangência é linha, não coluna anulável.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE cashback_rate (
    id          BIGSERIAL     PRIMARY KEY,
    scope       VARCHAR(10)   NOT NULL,
    scope_ref   VARCHAR(100),
    percent     NUMERIC(9,4)  NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    valid_from  TIMESTAMP     NOT NULL,
    valid_to    TIMESTAMP,
    created_at  TIMESTAMP     NOT NULL,
    CONSTRAINT ck_cashback_rate_scope CHECK (scope IN ('GLOBAL','CATEGORY','SKU')),
    CONSTRAINT ck_cashback_rate_scope_ref CHECK ((scope = 'GLOBAL') = (scope_ref IS NULL)),
    CONSTRAINT ck_cashback_rate_percent CHECK (percent >= 0 AND percent <= 100),
    CONSTRAINT ck_cashback_rate_validity CHECK (valid_to IS NULL OR valid_to > valid_from)
);

-- Só uma taxa ativa e em aberto (sem valid_to) por abrangência — evita duas regras GLOBAL, ou
-- duas regras para a mesma categoria/SKU, disputando a resolução ao mesmo tempo. Mesma lacuna já
-- aceita do projeto para índice único parcial (ex.: uk_cash_register_session_open_operator): o
-- H2 do perfil dev não exercita essa concorrência em teste.
CREATE UNIQUE INDEX uk_cashback_rate_active_scope
    ON cashback_rate (scope, COALESCE(scope_ref, ''))
    WHERE active AND valid_to IS NULL;

COMMENT ON COLUMN cashback_rate.scope_ref IS
    'NULL para GLOBAL; categoria ou SKU conforme scope. Taxa ZERO e taxa "sem regra" são coisas diferentes.';

-- ---------------------------------------------------------------------------------------------
-- Ledger de cashback, append-only: nenhuma linha é atualizada depois de escrita, nenhuma é
-- deletada. Sem coluna "status" — diferente do rascunho original do plano: disponibilidade é
-- sempre derivada de available_at comparado a now(), e o saldo é SUM(amount) WHERE
-- available_at <= now(). Cobre EARNED (positivo) e os futuros REDEEMED/REVERSED/EXPIRED
-- (negativos, com available_at = created_at porque um débito vale imediatamente) sem precisar
-- mutar a linha original. Ver CashbackEntry em core/domain/model/cashback.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE cashback_entry (
    id                BIGSERIAL     PRIMARY KEY,
    customer_id       BIGINT        NOT NULL REFERENCES customers(id),
    order_id          BIGINT        NOT NULL REFERENCES sales_order(id),
    order_item_id     BIGINT        REFERENCES order_item(id),
    type              VARCHAR(20)   NOT NULL,
    amount            NUMERIC(14,2) NOT NULL,
    available_at      TIMESTAMP     NOT NULL,
    expires_at        TIMESTAMP,
    reverses_entry_id BIGINT        REFERENCES cashback_entry(id),
    created_at        TIMESTAMP     NOT NULL,
    CONSTRAINT ck_cashback_entry_type CHECK (type IN ('EARNED','REDEEMED','REVERSED','EXPIRED')),
    CONSTRAINT ck_cashback_entry_amount_not_zero CHECK (amount <> 0),
    CONSTRAINT ck_cashback_entry_sign CHECK ((type = 'EARNED') = (amount > 0)),
    CONSTRAINT ck_cashback_entry_reversal CHECK ((type = 'EARNED') OR (reverses_entry_id IS NOT NULL))
);

-- Soma de saldo (disponível/pendente) por cliente.
CREATE INDEX idx_cashback_entry_customer_available ON cashback_entry (customer_id, available_at);

-- Varredura do scheduler de expiração: só EARNED tem expires_at relevante; quais já foram
-- expirados é checado por NOT EXISTS na consulta (ver CashbackEntryJpaRepository), não aqui.
CREATE INDEX idx_cashback_entry_earned_expiring
    ON cashback_entry (expires_at)
    WHERE type = 'EARNED';

COMMENT ON TABLE cashback_entry IS
    'Ledger append-only. Expiração e (futuramente) resgate/estorno escrevem uma NOVA linha negativa referenciando a original via reverses_entry_id — nunca mutam a linha original.';

-- ---------------------------------------------------------------------------------------------
-- Seed: taxa GLOBAL conservadora (decisão do dono, 2026-07-29 — 3%, não os 8% que o rascunho do
-- plano sugeria, porque 8% em carvão consome 44% da margem do item). Nenhuma taxa por categoria:
-- a loja ainda não tem categorias reais cadastradas em product.category (texto livre, sem
-- enum/tabela própria) — semear scope_ref com nomes inventados deixaria a regra morta
-- silenciosamente. Taxas por categoria/SKU entram depois, com os nomes reais do catálogo, via
-- POST /cashback/rates.
-- ---------------------------------------------------------------------------------------------
INSERT INTO cashback_rate (scope, scope_ref, percent, active, valid_from, created_at)
VALUES ('GLOBAL', NULL, 3.0000, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------------------------------------------------------------------------------------
-- Parâmetros de resgate (decisão do dono, 2026-07-29) — semeados agora para a fatia seguinte
-- (resgate no balcão, isolada desta) já nascer configurada. Nenhum código lê resgate.teto ou
-- resgate.minimo ainda; carência e expiração já são lidos por CashbackService.recordEarnedForOrder.
-- ---------------------------------------------------------------------------------------------
INSERT INTO system_config (config_key, config_value) VALUES
    ('cashback.carencia.dias', '7'),
    ('cashback.expiracao.dias', '180'),
    ('cashback.resgate.teto.percent', '50'),
    ('cashback.resgate.minimo.saldo', '50.00')
ON CONFLICT (config_key) DO NOTHING;

-- ---------------------------------------------------------------------------------------------
-- Permissões
-- ---------------------------------------------------------------------------------------------
INSERT INTO permissions (name) VALUES ('CASHBACK_RATE_MANAGE') ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('CASHBACK_READ') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('CASHBACK_RATE_MANAGE', 'CASHBACK_READ')
ON CONFLICT DO NOTHING;
