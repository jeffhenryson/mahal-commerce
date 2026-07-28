-- EST-F019 — Precificação de produto: custo, markup e preço praticado.
--
-- As três colunas são NULLABLE de propósito. Todo produto já cadastrado passa a existir como
-- "não precificado" (Pricing.empty()), sem backfill e sem valor default enganoso: preço zero
-- não é a mesma coisa que preço desconhecido, e um DEFAULT 0 aqui faria o PDV vender de graça
-- em vez de recusar a venda de item sem preço.
--
-- Preço mora no SKU pai; product_variant não recebe colunas de preço. Preço por variação é
-- EST-F020 no backlog do módulo.

ALTER TABLE product ADD COLUMN cost_price     NUMERIC(14,2);
ALTER TABLE product ADD COLUMN markup_percent NUMERIC(9,4);
ALTER TABLE product ADD COLUMN sale_price     NUMERIC(14,2);

-- Espelham as invariantes do compact constructor de Pricing. Redundantes por desenho: o domínio
-- é a primeira barreira, mas o schema é a que sobrevive a carga direta, script de correção e
-- import de planilha.
ALTER TABLE product ADD CONSTRAINT ck_product_cost_price_non_negative
    CHECK (cost_price IS NULL OR cost_price >= 0);
ALTER TABLE product ADD CONSTRAINT ck_product_markup_percent_non_negative
    CHECK (markup_percent IS NULL OR markup_percent >= 0);
ALTER TABLE product ADD CONSTRAINT ck_product_sale_price_non_negative
    CHECK (sale_price IS NULL OR sale_price >= 0);

-- Escala 4 em markup_percent (e não 2) porque markup é um input de fórmula, não um valor de
-- exibição: 33,3333% de markup sobre custo 45,00 tem que reproduzir 60,00 no preço sugerido.
-- Arredondar o input a 2 casas deslocaria o preço em centavos, e centavo errado em preço de
-- prateleira é divergência de caixa no fechamento.

COMMENT ON COLUMN product.cost_price IS
    'Custo de aquisição por unidade. NULL = desconhecido.';
COMMENT ON COLUMN product.markup_percent IS
    'Markup desejado sobre o CUSTO, em % (100 = dobrar o custo). Não confundir com margem, que é sobre a venda.';
COMMENT ON COLUMN product.sale_price IS
    'Preço praticado. Vence sobre o sugerido pelo markup — é onde mora o preço psicológico. NULL = usar o sugerido.';

-- Permissão dedicada: quem edita cadastro (nome, categoria, grade) não necessariamente pode
-- mexer em preço. ESTOQUE_PRODUCT_MANAGE continua liberando o cadastro; precificar exige esta.
INSERT INTO permissions (name) VALUES ('ESTOQUE_PRODUCT_PRICE_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'ESTOQUE_PRODUCT_PRICE_MANAGE'
ON CONFLICT DO NOTHING;
