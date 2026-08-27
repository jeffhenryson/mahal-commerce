-- Remove permissões DEV criadas pelo bootstrap mas sem endpoint correspondente.
-- DEV_LOGS_TECHNICAL, DEV_SYSTEM_CONFIG e DEV_DEBUG_ENDPOINTS foram planejadas
-- para uso futuro mas nunca protegeram nenhum endpoint.
-- Removidas do DevRoleBootstrapConfig: não serão recriadas em novas instâncias.
DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT id FROM permissions
    WHERE name IN ('DEV_LOGS_TECHNICAL', 'DEV_SYSTEM_CONFIG', 'DEV_DEBUG_ENDPOINTS')
);

DELETE FROM permissions
WHERE name IN ('DEV_LOGS_TECHNICAL', 'DEV_SYSTEM_CONFIG', 'DEV_DEBUG_ENDPOINTS');
