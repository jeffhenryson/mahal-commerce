CREATE TABLE system_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value TEXT        NOT NULL,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(255)
);

INSERT INTO system_config (config_key, config_value, updated_at, updated_by) VALUES
    ('auth.google.enabled',          'true',  CURRENT_TIMESTAMP, 'system'),
    ('auth.google.register.enabled', 'true',  CURRENT_TIMESTAMP, 'system'),
    ('auth.registration.enabled',    'true',  CURRENT_TIMESTAMP, 'system'),
    ('auth.forgot-password.enabled', 'true',  CURRENT_TIMESTAMP, 'system');
