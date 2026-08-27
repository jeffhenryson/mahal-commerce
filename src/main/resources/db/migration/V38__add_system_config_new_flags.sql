-- Adiciona flags de configuração de sistema criadas após V34.
-- ON CONFLICT DO NOTHING: idempotente em ambientes onde o DevRoleBootstrapConfig
-- ou scripts manuais já inseriram os valores.

INSERT INTO system_config (config_key, config_value) VALUES
    ('security.maintenance.enabled', 'false'),
    ('security.2fa.required',        'false'),
    ('module.audit-logs.enabled',    'true'),
    ('module.roles.enabled',         'true')
ON CONFLICT (config_key) DO NOTHING;
