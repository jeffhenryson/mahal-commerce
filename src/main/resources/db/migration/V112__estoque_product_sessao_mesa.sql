-- PDV-F010 — Sessão de narguilé na mesa: os quatro campos que o produto precisa para o lounge
-- vender por SESSÃO, e não só por unidade.
--
-- Contexto: a mesma essência sai a R$14 avulsa no balcão e a partir de R$25 quando vira sessão na
-- mesa. Não há campo `sessionPrice`: "Essência Blueberry" e "Sessão de narguilé" são dois produtos
-- distintos no catálogo, e os sabores da sessão são as VARIAÇÕES da grade — o `sale_price` de cada
-- variação já É o preço de sessão daquele sabor. Nada muda em product_variant.
--
-- Todos aditivos: nenhum produto já cadastrado muda de comportamento.

-- Disponibilidade na comanda de mesa. Ortogonal a visible_in_pos: bebida e narguilé saem na mesa e
-- no balcão; cigarro e isqueiro saem só no balcão. DEFAULT TRUE é obrigatório, não cosmético — é o
-- único jeito de todo produto já cadastrado preservar o comportamento implícito de hoje (sai na
-- mesa). Mesma escolha, e mesma razão, de visible_in_pos na V91.
ALTER TABLE product ADD COLUMN available_for_table BOOLEAN NOT NULL DEFAULT TRUE;

-- Vendido por sessão de mesa, não por unidade. DEFAULT FALSE porque a sessão é a exceção do
-- catálogo, não a regra: o produto de sessão é cadastrado à mão, marcado de propósito.
ALTER TABLE product ADD COLUMN session_product BOOLEAN NOT NULL DEFAULT FALSE;

-- Quantas sessões saem de uma unidade da origem (ex.: 5 sessões por lata).
--
-- NÃO movimenta saldo sozinho: é sugestão para o diálogo de conversão de estoque do admin, que faz
-- saída de 1 lata e entrada de N sessões pelo POST /estoque/movements que já existe. Lata e sessão
-- são SKUs distintos por decisão explícita — toda quantidade do sistema é inteira hoje
-- (step="1", movimentação inteira), e baixar 1/N por sessão mudaria o contrato de ajuste, contagem
-- e reposição de uma vez.
ALTER TABLE product ADD COLUMN sessions_per_unit INTEGER;
ALTER TABLE product ADD CONSTRAINT ck_product_sessions_per_unit_positive
    CHECK (sessions_per_unit IS NULL OR sessions_per_unit > 0);

-- Consumo livre (open rosh): valor fixo cobrado UMA VEZ por sessão, com trocas de sabor
-- ilimitadas. Mora no SKU pai de propósito — a linha da comanda chega com o SKU da VARIAÇÃO (para
-- saber qual essência sair do estoque), mas o valor cobrado é este. Ver ComandaService.addItem.
ALTER TABLE product ADD COLUMN open_rosh_price NUMERIC(14, 2);
ALTER TABLE product ADD CONSTRAINT ck_product_open_rosh_price_non_negative
    CHECK (open_rosh_price IS NULL OR open_rosh_price >= 0);

COMMENT ON COLUMN product.available_for_table IS
    'Produto pode ser lançado numa comanda de mesa. Default TRUE preserva o comportamento implícito do catálogo já cadastrado. Ortogonal a visible_in_pos.';
COMMENT ON COLUMN product.session_product IS
    'Vendido por sessão de mesa, não por unidade (PDV-F010). Os sabores são as variações da grade, e o preço de sessão de cada sabor é o sale_price da variação.';
COMMENT ON COLUMN product.sessions_per_unit IS
    'Quantas sessões saem de uma unidade da origem. Só sugestão para o diálogo de conversão de estoque do admin — não movimenta saldo sozinho.';
COMMENT ON COLUMN product.open_rosh_price IS
    'Consumo livre por valor fixo, cobrado uma única vez por sessão. Mora no SKU pai: a linha chega com o SKU da variação para saber o que sair do estoque, mas cobra este valor.';
