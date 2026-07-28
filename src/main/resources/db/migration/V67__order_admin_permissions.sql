-- Superfície /orders — a visão do administrador sobre pedidos de todos os canais.
--
-- Três permissões e não uma, porque as três operações têm consequências muito diferentes: ler é
-- inócuo, avançar estágio é operação de expedição, e cancelar DEVOLVE MERCADORIA AO ESTOQUE e
-- (a partir da Fatia 3) dispara estorno de pagamento. Uma permissão única obrigaria a conceder o
-- cancelamento para quem só precisa despachar pedido.

INSERT INTO permissions (name) VALUES ('ORDER_READ')
ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('ORDER_FULFILL')
ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('ORDER_CANCEL')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('ORDER_READ', 'ORDER_FULFILL', 'ORDER_CANCEL')
ON CONFLICT DO NOTHING;
