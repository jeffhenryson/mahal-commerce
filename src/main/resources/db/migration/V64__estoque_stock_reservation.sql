-- EST-F021 — Reserva de estoque: disponível = físico - reservado.
--
-- É a peça que permite balcão e marketplace consumirem o MESMO depósito sem overselling. O tipo
-- de depósito ECOMMERCE já existia e seria o caminho óbvio para separar os canais, mas numa loja
-- só a prateleira é uma só: partir o saldo em dois pools criaria rebalanceamento manual permanente
-- e a situação de "o site tem 5 e a loja tem 0" com tudo no mesmo armário.
--
-- O PDV continua dando baixa imediata (SAIDA), porque no balcão a mercadoria sai na hora. Quem
-- reserva é o checkout online, na janela entre montar o pedido e o pagamento confirmar.

-- ---------------------------------------------------------------------------------------------
-- Contador de reservado no próprio saldo
-- ---------------------------------------------------------------------------------------------
-- Mora aqui, e não numa soma sobre stock_reservation, para cair sob o @Version que a linha já tem:
-- duas reservas concorrentes do mesmo SKU disputam esta linha e uma sai em 409, exatamente como
-- duas movimentações concorrentes já se comportam. Uma agregação à parte não travaria nada.
--
-- DEFAULT 0 é honesto aqui, ao contrário do que seria em product.cost_price (V63): "nada
-- reservado" é o estado real de todo saldo existente, não um valor desconhecido disfarçado.
ALTER TABLE stock_balance ADD COLUMN reserved_quantity NUMERIC(14,3) NOT NULL DEFAULT 0;

-- Espelha as invariantes do compact constructor de StockBalance. Redundante por desenho: o domínio
-- é a primeira barreira, o schema é a que sobrevive a carga direta e script de correção.
ALTER TABLE stock_balance ADD CONSTRAINT ck_stock_balance_reserved_non_negative
    CHECK (reserved_quantity >= 0);
ALTER TABLE stock_balance ADD CONSTRAINT ck_stock_balance_reserved_within_quantity
    CHECK (reserved_quantity <= quantity);

COMMENT ON COLUMN stock_balance.reserved_quantity IS
    'Parte de quantity já prometida a pedido não concluído. Disponível para venda = quantity - reserved_quantity.';

-- ---------------------------------------------------------------------------------------------
-- Ledger de reservas
-- ---------------------------------------------------------------------------------------------
-- Mesma divisão do resto do módulo: stock_balance é o estado, esta tabela é o histórico de quem
-- reservou, para quê, até quando e como terminou.
CREATE TABLE stock_reservation (
    id              BIGSERIAL      PRIMARY KEY,
    sku             VARCHAR(50)    NOT NULL,
    warehouse_id    BIGINT         NOT NULL REFERENCES warehouse(id),
    quantity        NUMERIC(14,3)  NOT NULL,
    owner_reference VARCHAR(80)    NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    expires_at      TIMESTAMP      NOT NULL,
    created_at      TIMESTAMP      NOT NULL,
    resolved_at     TIMESTAMP,
    username        VARCHAR(80)    NOT NULL,

    CONSTRAINT ck_stock_reservation_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_stock_reservation_status
        CHECK (status IN ('ACTIVE','CONSUMED','RELEASED','EXPIRED')),
    CONSTRAINT ck_stock_reservation_expires_after_created CHECK (expires_at > created_at),
    -- Reserva ativa ainda não terminou; reserva terminada tem que dizer quando.
    CONSTRAINT ck_stock_reservation_resolved_consistency
        CHECK ((status = 'ACTIVE') = (resolved_at IS NULL))
);

-- Sem FK para product(sku): o SKU pai e o de variação compartilham o espaço de nomes em duas
-- tabelas distintas (product e product_variant), então não há coluna única para referenciar. A
-- validação fica no service, como já ocorre em adjustStock (EST-C002).

CREATE INDEX idx_stock_reservation_sku_warehouse ON stock_reservation (sku, warehouse_id);
CREATE INDEX idx_stock_reservation_owner ON stock_reservation (owner_reference);

-- Índice parcial para o varredor de expiração: ele só olha reserva ATIVA e vencida, e essa fatia
-- é minúscula perto do histórico acumulado de reservas já resolvidas.
CREATE INDEX idx_stock_reservation_sweep ON stock_reservation (expires_at)
    WHERE status = 'ACTIVE';

COMMENT ON COLUMN stock_reservation.owner_reference IS
    'Quem pediu a reserva. Hoje é o identificador de checkout do chamador; passa a ser ORDER:{id} quando o domínio de pedido existir. Texto e não FK de propósito — FK para tabela inexistente seria coluna morta.';
COMMENT ON COLUMN stock_reservation.expires_at IS
    'A partir daqui o varredor devolve a quantidade ao disponível. Obrigatório: reserva sem prazo é saldo perdido sem ninguém perceber.';

-- ---------------------------------------------------------------------------------------------
-- RBAC
-- ---------------------------------------------------------------------------------------------
-- MANAGE e READ separados porque reservar segura saldo de venda: quem consulta o disponível na
-- vitrine não precisa poder comprometê-lo.
INSERT INTO permissions (name) VALUES ('ESTOQUE_RESERVATION_READ')
ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('ESTOQUE_RESERVATION_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('ESTOQUE_RESERVATION_READ', 'ESTOQUE_RESERVATION_MANAGE')
ON CONFLICT DO NOTHING;
