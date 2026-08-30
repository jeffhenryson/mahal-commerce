-- PDV-F014 — Permissão para abater da conta no fechamento da mesa.
--
-- A venda de balcão tem desconto desde PDV-F004, sob PDV_SALE_DISCOUNT e com teto em
-- pdv.sale.max-discount-percent. Na mesa o desconto de fim de noite não tinha onde ir: ou se
-- lançava linha de cortesia (que baixa estoque e é outra coisa), ou se cobrava por fora.
--
-- Permissão PRÓPRIA, e não a reutilização de PDV_SALE_DISCOUNT, seguindo o que o módulo já fez
-- duas vezes: PDV_COMANDA_COURTESY (V115) e PDV_COMANDA_SURCHARGE (V117) também são de mesa e
-- separadas das do balcão. Alçada de mesa e alçada de caixa são concedidas a pessoas diferentes —
-- quem fecha o salão à noite não é necessariamente quem opera o balcão de dia —, e granularidade
-- separada não custa mais que uma linha de checagem no controller.
--
-- O TETO é compartilhado (pdv.sale.max-discount-percent, default 10%): o limite é uma política
-- comercial da casa, não uma característica do canal, e duplicá-lo em duas chaves de configuração
-- criaria a chance de elas divergirem em silêncio.
--
-- Concedida só ao ROLE_ADMIN, como a V115 e a V117. Estender ao atendente depois é uma migration
-- de uma linha; recolher uma permissão já distribuída é o caminho caro.

INSERT INTO permissions (name) VALUES ('PDV_COMANDA_DISCOUNT')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'PDV_COMANDA_DISCOUNT'
ON CONFLICT DO NOTHING;
