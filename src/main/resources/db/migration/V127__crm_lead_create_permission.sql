-- CRM-C006 / PDV-F020 — Atendente cadastra lead no balcão e na mesa.
--
-- Bug de uso: o cadastro de cliente pela mesa e pelo PDV não salvava quando operado por atendente.
-- POST /crm/customers exige CRM_CUSTOMER_MANAGE, que desde a V48 é só do ROLE_ADMIN; a V86 deu ao
-- atendente apenas READ e LOOKUP. O atendente tomava 403 e o PDV engolia o erro, fechando a venda
-- como anônima.
--
-- Não estendemos CRM_CUSTOMER_MANAGE ao atendente: ela também guarda tags, notas, estágio do kanban
-- e disparo de automações de campanha. CRM_LEAD_CREATE libera só o cadastro rápido
-- (POST /crm/customers e o find-or-create POST /crm/customers/lead).

INSERT INTO permissions (name) VALUES ('CRM_LEAD_CREATE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_ATENDENTE') AND p.name = 'CRM_LEAD_CREATE'
ON CONFLICT DO NOTHING;
