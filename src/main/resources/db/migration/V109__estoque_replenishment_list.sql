-- Lista de Reposição por depósito (item 1 do pedido do frontend) — recurso novo, mais urgente do
-- levantamento. Hoje a lista de compras vive em localStorage no navegador
-- (ss_estoque_lista_reposicao): o gerente anota no computador do caixa e não vê nada no notebook
-- de casa, dois operadores anotando ao mesmo tempo montam listas que nunca se encontram, e limpar
-- o navegador apaga tudo.
--
-- Os campos *_snapshot são deliberadamente congelados no momento do POST, nunca recalculados na
-- leitura (decisão do produto): a lista é um rascunho de compra, e se o saldo mudar depois de
-- anotado, a intenção de compra continua valendo e o histórico do porquê não se perde.
--
-- sku SEM FK para product, mesma convenção livre de stock_balance/stock_movement/
-- stock_reorder_point (EST-C011) — nenhuma tabela de estoque referencia o catálogo por FK.

CREATE TABLE replenishment_list_item (
    id                                BIGSERIAL     PRIMARY KEY,
    sku                               VARCHAR(50)   NOT NULL,
    warehouse_id                     BIGINT        NOT NULL REFERENCES warehouse (id),
    product_name_snapshot            VARCHAR(255),
    category_snapshot                VARCHAR(100),
    brand_snapshot                   VARCHAR(100),
    unit_snapshot                    VARCHAR(10),
    current_stock_snapshot           NUMERIC(14,3),
    min_stock_snapshot               NUMERIC(14,3),
    suggested_quantity_snapshot      NUMERIC(14,3),
    quantity                         NUMERIC(14,3) NOT NULL,
    unit_cost_snapshot               NUMERIC(14,2),
    previous_purchase_qty_snapshot   NUMERIC(14,3),
    previous_purchase_cost_snapshot  NUMERIC(14,2),
    previous_purchased_at_snapshot   TIMESTAMPTZ,
    note                              VARCHAR(500),
    created_at                       TIMESTAMPTZ   NOT NULL,
    created_by                       VARCHAR(80)   NOT NULL,
    CONSTRAINT uk_replenishment_sku_warehouse UNIQUE (sku, warehouse_id)
);

INSERT INTO permissions (name)
VALUES ('ESTOQUE_REPLENISHMENT_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'ESTOQUE_REPLENISHMENT_MANAGE'
ON CONFLICT DO NOTHING;

COMMENT ON TABLE replenishment_list_item IS
    'Lista de reposição por depósito — rascunho de compra. Campos *_snapshot congelados no momento da anotação, nunca recalculados na leitura.';
COMMENT ON COLUMN replenishment_list_item.sku IS
    'Texto livre, sem FK para product — mesma convenção de stock_balance/stock_movement/stock_reorder_point (EST-C011).';
