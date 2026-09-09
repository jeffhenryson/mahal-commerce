-- EST-F027 / PDV-F018 — a lata de essência aberta.
--
-- O PROBLEMA, MEDIDO. Uma lata rende várias sessões, e o catálogo declara quantas em
-- product.sessions_per_unit desde a V112 — só que aquele campo nasceu como "sugestão de tela" e
-- nunca foi lido por ninguém. Cada sessão lançada na comanda baixava uma LATA INTEIRA: no QA de
-- 06/09/2026, ESSE-ZGY-BLUEBERRY foi de 50 para 49 numa sessão só. Com sessions_per_unit = 5, o
-- estoque some cinco vezes mais rápido que a realidade — o alerta de reposição dispara cedo, o
-- custo por sessão fica inflado e a margem do open rosh, que é o número que o dono quer olhar,
-- sai errada.
--
-- A BAIXA ACONTECE NA ABERTURA. É o que mantém o significado do saldo igual ao que o operador
-- conta no balanço: stock_balance passa a ser "latas lacradas na prateleira", e a lata em uso vive
-- aqui, com o contador. Nenhum movimento é inventado — a SAIDA de 1 acontece no instante físico em
-- que alguém tira a lata da prateleira.
--
-- NADA DE HISTÓRICO É REPROCESSADO. Latas nascem zeradas a partir daqui e o saldo atual fica como
-- está. Recalcular comandas antigas geraria movimento retroativo sem lastro físico — ninguém sabe
-- quantas latas de fato foram abertas semana passada.
--
-- ESCOPO POR DEPÓSITO, coerente com o resto do módulo: dois balcões no mesmo armazém compartilham
-- a lata, que é o comportamento físico correto.

CREATE TABLE open_package (
    id                BIGSERIAL    PRIMARY KEY,
    sku               VARCHAR(50)  NOT NULL,
    warehouse_id      BIGINT       NOT NULL REFERENCES warehouse (id),
    uses              INTEGER      NOT NULL DEFAULT 0,
    sessions_per_unit INTEGER      NOT NULL,
    opened_at         TIMESTAMP    NOT NULL,
    opened_by         VARCHAR(100) NOT NULL,
    closed_at         TIMESTAMP    NULL,
    close_reason      VARCHAR(20)  NULL,
    CONSTRAINT ck_open_package_uses CHECK (uses >= 0 AND uses <= sessions_per_unit),
    CONSTRAINT ck_open_package_sessions_per_unit CHECK (sessions_per_unit > 0),
    -- Fechada e sem motivo — ou o inverso — é meio estado, e o compact constructor do domínio
    -- recusa os dois; o banco recusa junto, para carga direta não abrir a exceção.
    CONSTRAINT ck_open_package_closed CHECK ((closed_at IS NULL) = (close_reason IS NULL)),
    CONSTRAINT ck_open_package_close_reason CHECK (close_reason IS NULL OR close_reason IN ('EXHAUSTED', 'REPLACED'))
);

-- Uma lata em uso por par SKU/depósito. Índice único PARCIAL, e não UNIQUE simples, porque a
-- tabela é histórico: guarda todas as latas já fechadas do mesmo par, e uma constraint simples
-- proibiria a segunda. Mesmo molde dos índices parciais da V75.
CREATE UNIQUE INDEX uk_open_package_sku_warehouse_open
    ON open_package (sku, warehouse_id) WHERE closed_at IS NULL;

-- Leitura da tela de acompanhamento: as latas em uso de um depósito.
CREATE INDEX idx_open_package_warehouse_open
    ON open_package (warehouse_id) WHERE closed_at IS NULL;

-- sku sem FK para product, mesma decisão de stock_balance/stock_movement (EST-C011): o SKU pode
-- ser de variação, que vive em outra tabela, e a checagem de existência mora no service.

COMMENT ON TABLE open_package IS
    'Lata/pacote aberto rendendo sessões (EST-F027). A unidade já saiu de stock_balance na abertura; aqui fica o contador de usos.';
COMMENT ON COLUMN open_package.sessions_per_unit IS
    'Cópia de product.sessions_per_unit no momento da abertura. Cópia e não leitura: editar o catálogo não pode mudar o tamanho de uma lata pela metade.';
COMMENT ON COLUMN open_package.uses IS
    'Sessões já lançadas nesta lata. Cancelamento de comanda DECREMENTA este contador em vez de devolver unidade ao estoque — a essência foi queimada, não voltou para a prateleira.';

-- ── O lado da comanda (PDV-F018) ────────────────────────────────────────────
--
-- Qual uso da lata cada linha foi, congelado no lançamento. Dois campos e não um vínculo com
-- open_package.id de propósito: é SNAPSHOT. O histórico da mesa continua verdadeiro depois que a
-- lata for reposta, a tela mostra "3 de 5" por linha sem uma segunda chamada, e — o que mais
-- importa — é por aqui que o CANCELAMENTO sabe, meses depois, que aquela linha consumiu USO e não
-- unidade. Reler o catálogo não daria essa resposta: o produto pode ter deixado de ser vendido por
-- sessão desde então, e o desfazimento tem que espelhar o que aconteceu, não o cadastro de hoje.
--
-- NULL é a linha que baixou uma unidade inteira — toda linha anterior a esta migration, e toda
-- linha de produto que não é vendido por sessão. Nenhum backfill: não há como saber quantas latas
-- de fato foram abertas no passado, e chutar um contador é pior do que admitir que não se sabe.

ALTER TABLE comanda_item ADD COLUMN package_uses INTEGER NULL;
ALTER TABLE comanda_item ADD COLUMN package_sessions_per_unit INTEGER NULL;

-- Os dois andam juntos ou não existem: um sem o outro é "3 de ?" na tela. Mesma invariante do
-- compact constructor de ComandaItem, espelhada aqui para carga direta não abrir a exceção.
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_package_counter
    CHECK ((package_uses IS NULL) = (package_sessions_per_unit IS NULL));
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_package_positive
    CHECK (package_uses IS NULL OR (package_uses > 0 AND package_sessions_per_unit > 0));

COMMENT ON COLUMN comanda_item.package_uses IS
    'Qual uso da lata esta linha foi (EST-F027), congelado no lançamento. NULL = a linha baixou uma unidade inteira, e é assim que ela é desfeita se a mesa for cancelada.';
