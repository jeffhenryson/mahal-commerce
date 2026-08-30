-- PDV-F015 — Taxa de serviço da mesa: os 10% do garçom.
--
-- Até aqui a taxa não existia em lugar nenhum do sistema (uma varredura por service_fee, couvert e
-- gorjeta voltava vazia). Era somada de cabeça e cobrada por fora, o que significa que ela NÃO
-- entrava no fechamento de caixa, NÃO aparecia no comprovante e NÃO era conferível.
--
-- ---------------------------------------------------------------------------------------------
-- Por que coluna própria, e não dentro de net_amount
-- ---------------------------------------------------------------------------------------------
-- net_amount = total_amount - discount_amount - cashback_redeemed é somado como RECEITA em quatro
-- agregações (sumConcludedNetAmountBySessionId, findRevenueTotals, por canal e por dia). A gorjeta
-- é do garçom: a loja apenas a repassa. Somá-la ao líquido inflaria receita e margem com dinheiro
-- que não é da casa — e obrigaria toda agregação futura a lembrar de subtraí-la.
--
-- O que o cliente paga é net_amount + service_fee_amount, e é contra esse total que o pagamento é
-- validado e o troco calculado. A conferência da gaveta continua correta sem mudança nenhuma:
-- closeSession soma order_payment, não net_amount — o dinheiro da taxa passa pela gaveta como
-- qualquer outro.
--
-- Mesma família de decisão do surcharge_amount da V116: valor que compõe o que se cobra, guardado
-- em coluna à parte para o relatório separar as parcelas depois.

ALTER TABLE sales_order ADD COLUMN service_fee_amount NUMERIC(14,2) NOT NULL DEFAULT 0;

-- NOT NULL DEFAULT 0 sem backfill condicional: nenhum pedido gravado antes desta entrega cobrou
-- taxa, então zero é o valor historicamente verdadeiro — diferente de cost_price na V65, onde um
-- zero mentiria sobre a margem e por isso as colunas nasceram nulas.

ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_service_fee_non_negative
    CHECK (service_fee_amount >= 0);

-- Serviço de mesa só existe onde há mesa. No balcão não há o que cobrar, e no marketplace muito
-- menos. Espelha a invariante do compact constructor de Order, pela mesma razão que
-- ck_sales_order_change_only_balcao existe: sobreviver a carga direta e a script de correção.
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_service_fee_only_mesa
    CHECK (service_fee_amount = 0 OR channel = 'MESA');

COMMENT ON COLUMN sales_order.service_fee_amount IS
    'Taxa de serviço da mesa (PDV-F015), percentual sobre o líquido. FORA de net_amount de propósito: o líquido é receita da casa, a taxa é repasse ao garçom. O que o cliente paga é net_amount + service_fee_amount.';
