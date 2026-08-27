-- Adiciona informações de dispositivo/origem às sessões de refresh token.
-- Permite que usuários identifiquem e revoguem sessões suspeitas em GET /auth/sessions.
-- Colunas opcionais (nullable) para compatibilidade com tokens emitidos antes desta migration
-- e para contextos sem request HTTP (ex: testes, jobs em background).

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS ip_address  VARCHAR(45),   -- IPv4 (15) ou IPv6 (39), com margem
    ADD COLUMN IF NOT EXISTS user_agent  VARCHAR(512);  -- truncado a 512 para evitar payloads grandes
