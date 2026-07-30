-- PDV-F007 (Fatia 5) — permissão de reembolso, separada de ORDER_CANCEL: reembolso estorna
-- dinheiro de verdade (pagamento e cashback), cancelamento pré-pagamento não mexe em nenhum dos
-- dois. Conceder as duas juntas obrigaria quem só cancela pedido pré-pagamento a também poder
-- estornar pagamento.

INSERT INTO permissions (name) VALUES ('ORDER_REFUND')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'ORDER_REFUND'
ON CONFLICT DO NOTHING;
