-- Contrato de escrita FIN-F004 (front `mahal-admin`, Docs/BACKEND_TODO.md, 2026-08-18),
-- reproduzido em docs/dominios/financeiro/README.md. Primeira persistência real do módulo
-- financeiro — LedgerRepository era só um port sem adapter até aqui.
--
-- amount é sempre positivo, o sinal vem de direction — mesma convenção de cash_movement (V66).
-- due_date é NOT NULL: todo lançamento de Contas a Pagar/Receber tem vencimento; date (data de
-- lançamento) é atribuída pelo servidor na criação, separada de payment_date (pagamento efetivo).
--
-- deleted_at é soft-delete: DELETE só apaga a linha de verdade quando não há linked_entity_id —
-- havendo vínculo com pedido/compra de origem, a linha fica marcada mas preservada (contrato
-- explícito do front, "não perder o vínculo com o pedido/venda de origem").
--
-- linked_entity_id é id solto, sem FK: aponta para sales_order OU goods_receipt conforme
-- linked_entity_type, e uma FK de banco não suporta apontar para tabelas diferentes.

CREATE TABLE cash_flow_entry (
    id                 BIGSERIAL     PRIMARY KEY,
    date               DATE          NOT NULL,
    description        VARCHAR(255)  NOT NULL,
    entity_name        VARCHAR(150),
    category           VARCHAR(30)   NOT NULL,
    direction          VARCHAR(10)   NOT NULL,
    amount             NUMERIC(14,2) NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'PREVISTO',
    due_date           DATE          NOT NULL,
    payment_date       DATE,
    linked_entity_type VARCHAR(20),
    linked_entity_id   BIGINT,
    deleted_at         TIMESTAMP,
    CONSTRAINT ck_cash_flow_entry_category CHECK (category IN (
        'FORNECEDOR_PRODUTO', 'ALUGUEL', 'FOLHA_PAGAMENTO', 'PRO_LABORE', 'TAXA_CARTAO_PIX',
        'SOFTWARE_TI', 'ENERGIA_AGUA', 'VENDA_PDV', 'VENDA_ECOMMERCE', 'OUTROS')),
    CONSTRAINT ck_cash_flow_entry_direction CHECK (direction IN ('INFLOW', 'OUTFLOW')),
    CONSTRAINT ck_cash_flow_entry_status CHECK (status IN ('PREVISTO', 'PAGO', 'ATRASADO')),
    CONSTRAINT ck_cash_flow_entry_linked_entity_type CHECK (linked_entity_type IN ('ORDER', 'PURCHASE')),
    CONSTRAINT ck_cash_flow_entry_amount_positive CHECK (amount > 0)
);

-- Contas a Pagar/Receber filtra e ordena por vencimento; summary agrega por due_date no período.
CREATE INDEX idx_cash_flow_entry_due_date ON cash_flow_entry (due_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_cash_flow_entry_status ON cash_flow_entry (status) WHERE deleted_at IS NULL;

-- A tabela `permissions` (V4__permissions.sql) só tem `id` e `name` — nunca teve `description`.
INSERT INTO permissions (name)
VALUES ('FINANCEIRO_CASH_FLOW_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'FINANCEIRO_CASH_FLOW_MANAGE'
ON CONFLICT DO NOTHING;

COMMENT ON TABLE cash_flow_entry IS
    'Lançamento de fluxo de caixa (FIN-F004). status é coluna mutável via PATCH, não ledger append-only.';
COMMENT ON COLUMN cash_flow_entry.linked_entity_id IS
    'Vínculo opcional com sales_order ou goods_receipt, conforme linked_entity_type. Sem FK de banco: aponta para tabelas diferentes.';
COMMENT ON COLUMN cash_flow_entry.deleted_at IS
    'Soft-delete. Preenchido só quando linked_entity_id não é nulo — sem vínculo, DELETE remove a linha.';
