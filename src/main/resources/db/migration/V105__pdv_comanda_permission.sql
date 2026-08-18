-- PDV-F009 — permissão de comanda, separada de PDV_SALE_MANAGE: comanda é uma superfície
-- operacional diferente de venda pontual de balcão (tab de horas vs. venda instantânea), e manter
-- a concessão separada não custa nada além desta linha a mais de @PreAuthorize. Leitura continua
-- sob PDV_READ, já bastante distribuída no módulo.

INSERT INTO permissions (name) VALUES ('PDV_COMANDA_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'PDV_COMANDA_MANAGE'
ON CONFLICT DO NOTHING;
