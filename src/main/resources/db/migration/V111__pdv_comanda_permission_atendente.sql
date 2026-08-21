-- PDV-F009 — correção da V105: PDV_COMANDA_MANAGE nasceu concedida apenas ao ROLE_ADMIN, mas quem
-- opera comanda de mesa é o atendente. A V86 já deu PDV_READ ao ROLE_ATENDENTE, então ele abre
-- /pdv/comandas e lista as mesas ocupadas, mas abrir/lançar/fechar/cancelar respondia 403.
-- Cancelar entra junto na mesma permissão por decisão explícita: o estorno de estoque do
-- abandono já é auditado (origin=PDV_COMANDA_CANCEL), e separar exigiria uma permissão só para
-- ele sem ganho operacional no balcão.

INSERT INTO permissions (name) VALUES ('PDV_COMANDA_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ATENDENTE' AND p.name = 'PDV_COMANDA_MANAGE'
ON CONFLICT DO NOTHING;
