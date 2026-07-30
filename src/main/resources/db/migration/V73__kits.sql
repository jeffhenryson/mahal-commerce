-- EST-F015 (Fatia 6) — kit virtual, um nível só (docs/plano-pdv-marketplace.md §2.10).
--
-- Kit é derivado: explode em componentes na venda/estorno, nunca tem saldo próprio em
-- stock_balance. Componente precisa ser SIMPLES — kit dentro de kit é proibido por construção,
-- não detectado por travessia (ck_kit_component_not_self mata a autorreferência; a regra
-- "componente é SIMPLES" não é checável aqui porque product_kit_component não tem FK para
-- product — ver comentário abaixo — então fica só no service).
--
-- Sem FK component_sku/kit_sku -> product(sku): a coluna não é única no espaço de nomes
-- compartilhado pai/variação (um componente pode ser SKU de variação, não só SKU pai). A
-- validação "componente existe e é SIMPLES" fica no service, como já ocorre em requireKnownSku
-- para as outras tabelas de estoque sem FK (stock_balance, stock_movement).

ALTER TABLE product ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'SIMPLES';
ALTER TABLE product ADD CONSTRAINT ck_product_type CHECK (type IN ('SIMPLES','KIT'));

CREATE TABLE product_kit_component (
    id            BIGSERIAL     PRIMARY KEY,
    kit_sku       VARCHAR(50)   NOT NULL,
    component_sku VARCHAR(50)   NOT NULL,
    quantity      NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    CONSTRAINT uk_kit_component UNIQUE (kit_sku, component_sku),
    CONSTRAINT ck_kit_component_not_self CHECK (kit_sku <> component_sku)
);

CREATE INDEX idx_kit_component_kit_sku ON product_kit_component (kit_sku);

COMMENT ON TABLE product_kit_component IS
    'Receita de kit virtual (EST-F015). Uma linha por componente: quantity unidades de component_sku são consumidas por unidade vendida de kit_sku. Substituída por inteiro a cada PUT /estoque/products/{sku}/kit, nunca mesclada.';

-- Receita de kit muda o que é consumido em toda venda futura daquele SKU — mesma classe de
-- preocupação que já justificou ESTOQUE_PRODUCT_PRICE_MANAGE separado de ESTOQUE_PRODUCT_MANAGE.
INSERT INTO permissions (name) VALUES ('ESTOQUE_KIT_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'ESTOQUE_KIT_MANAGE'
ON CONFLICT DO NOTHING;
