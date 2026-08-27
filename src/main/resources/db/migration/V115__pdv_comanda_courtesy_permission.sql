-- PDV-F010 — Permissão para lançar cortesia na comanda.
--
-- Separada de PDV_COMANDA_MANAGE de propósito: lançar uma linha a preço zero é um desconto de 100%,
-- e o projeto já tratou desconto como decisão com dono em PDV_SALE_DISCOUNT. Quem opera a mesa não
-- é necessariamente quem pode dar sessão de graça — a promo de domingo e a de quarta são decisão da
-- casa, não do atendente de plantão.
--
-- Concedida só ao ROLE_ADMIN, ao contrário da V111 (que estendeu PDV_COMANDA_MANAGE ao atendente).
-- Se a operação decidir que todo atendente pode aplicar a promo, é uma migration de uma linha —
-- o caminho contrário, tirar a permissão depois de distribuída, é o que custa caro.

INSERT INTO permissions (name) VALUES ('PDV_COMANDA_COURTESY')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'PDV_COMANDA_COURTESY'
ON CONFLICT DO NOTHING;
