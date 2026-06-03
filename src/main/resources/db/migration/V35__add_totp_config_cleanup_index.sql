-- Índice para o scheduler de limpeza de setups TOTP pendentes (enabled=false AND created_at < :before).
-- Sem este índice o DELETE faz full scan conforme a tabela totp_config cresce.
CREATE INDEX idx_totp_config_cleanup ON totp_config(enabled, created_at) WHERE enabled = false;
