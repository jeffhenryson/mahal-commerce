-- PDV-F001/F002/C004 — Ciclo de caixa: abertura, sangria, suprimento e fechamento com conferência.
--
-- É a lacuna que impede a loja de confiar no sistema: hoje abrir um caixa exige INSERT manual, não
-- há registro de quem abriu nem com qual fundo de troco, e sem conferência no fechamento não há como
-- distinguir erro de troco de desvio.
--
-- O desenho espelha o balanço de inventário (EST-F006), que já resolveu o mesmo problema com
-- mercadoria no lugar de dinheiro: confronta contado x esperado, carimba a divergência e FECHA MESMO
-- ASSIM. Bloquear o encerramento por divergência só produziria caixas que nunca fecham.

-- ---------------------------------------------------------------------------------------------
-- Sessão de caixa
-- ---------------------------------------------------------------------------------------------
ALTER TABLE cash_register_session ADD COLUMN warehouse_code    VARCHAR(50);
ALTER TABLE cash_register_session ADD COLUMN closed_by         VARCHAR(80);
ALTER TABLE cash_register_session ADD COLUMN expected_amount   NUMERIC(14,2);
ALTER TABLE cash_register_session ADD COLUMN counted_amount    NUMERIC(14,2);
ALTER TABLE cash_register_session ADD COLUMN difference_amount NUMERIC(14,2);

-- Backfill: as sessões existentes foram criadas por INSERT manual e não dizem de qual depósito
-- venderam. O primeiro depósito de loja física é o único palpite honesto — numa tabacaria de uma
-- loja ele é também o único que existe.
UPDATE cash_register_session SET warehouse_code =
    (SELECT code FROM warehouse WHERE type = 'LOJA_FISICA' ORDER BY id LIMIT 1)
    WHERE warehouse_code IS NULL;

ALTER TABLE cash_register_session ALTER COLUMN warehouse_code SET NOT NULL;

-- Uma sessão aberta por operador. Índice PARCIAL: a restrição vale só enquanto o caixa está aberto,
-- porque o mesmo operador abre e fecha um caixa todo dia. O domínio já checa antes de abrir, mas é
-- este índice que sobrevive a duas requisições simultâneas.
CREATE UNIQUE INDEX uk_cash_register_session_open_operator
    ON cash_register_session (operator) WHERE status = 'OPEN';

-- Espelha o compact constructor de CashRegisterSession.
ALTER TABLE cash_register_session ADD CONSTRAINT ck_cash_register_session_closed_consistency
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL AND counted_amount IS NOT NULL));
ALTER TABLE cash_register_session ADD CONSTRAINT ck_cash_register_session_amounts
    CHECK (opening_amount >= 0 AND (counted_amount IS NULL OR counted_amount >= 0));

COMMENT ON COLUMN cash_register_session.warehouse_code IS
    'Depósito de onde sai a mercadoria vendida neste caixa. Mora na sessão, e não no request de cada venda, para o operador não baixar estoque de depósito alheio (PDV-C004).';
COMMENT ON COLUMN cash_register_session.difference_amount IS
    'counted_amount - expected_amount. NEGATIVO significa falta na gaveta, e é um número legítimo: divergência é o achado do fechamento, não um erro que o bloqueia.';
COMMENT ON COLUMN cash_register_session.expected_amount IS
    'Derivado no fechamento: abertura + vendas - sangrias + suprimentos. Enquanto order_payment não existir (Fatia 3), usa o líquido de TODOS os pedidos concluídos na sessão, sem distinguir a forma de pagamento.';

-- ---------------------------------------------------------------------------------------------
-- Ledger de movimentos de caixa
-- ---------------------------------------------------------------------------------------------
-- Ledger, e não um contador na sessão, pela mesma razão do stock_movement: o valor esperado é
-- DERIVADO destes lançamentos, e um contador mutável tornaria impossível responder "por que o
-- esperado é esse?" no dia em que o fechamento não bater.
CREATE TABLE cash_movement (
    id         BIGSERIAL     PRIMARY KEY,
    session_id BIGINT        NOT NULL REFERENCES cash_register_session(id) ON DELETE CASCADE,
    type       VARCHAR(20)   NOT NULL,
    amount     NUMERIC(14,2) NOT NULL,
    reason     VARCHAR(255)  NOT NULL,
    username   VARCHAR(80)   NOT NULL,
    created_at TIMESTAMP     NOT NULL,

    -- O sinal vem do type, nunca do valor. Permitir amount negativo criaria duas representações
    -- para a mesma sangria, e todo SUM sobre a tabela passaria a depender de quem gravou.
    CONSTRAINT ck_cash_movement_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_cash_movement_type CHECK (type IN ('SANGRIA','SUPRIMENTO'))
);

CREATE INDEX idx_cash_movement_session_id ON cash_movement (session_id);

COMMENT ON COLUMN cash_movement.amount IS
    'Sempre positivo. O efeito no caixa vem do type: SANGRIA subtrai, SUPRIMENTO soma.';
COMMENT ON COLUMN cash_movement.reason IS
    'Obrigatório: sangria sem motivo registrado é indistinguível de desvio, e o momento de anotar o porquê é o da retirada, não o da conferência.';

-- ---------------------------------------------------------------------------------------------
-- RBAC
-- ---------------------------------------------------------------------------------------------
-- Abrir caixa e registrar sangria é operação do turno; fechar é conferência, e quem confere não
-- deveria ser necessariamente quem operou.
INSERT INTO permissions (name) VALUES ('PDV_SESSION_MANAGE')
ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('PDV_SESSION_CLOSE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('PDV_SESSION_MANAGE', 'PDV_SESSION_CLOSE')
ON CONFLICT DO NOTHING;
