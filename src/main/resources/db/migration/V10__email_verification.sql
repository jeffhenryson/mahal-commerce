-- Adiciona campos de email e verificação na tabela users
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email VARCHAR(254),
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email ON users (email) WHERE email IS NOT NULL;

-- Tabela de códigos de verificação de email (TTL curto; registros removidos após uso)
CREATE TABLE IF NOT EXISTS email_verification_codes (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(80)  NOT NULL,
    code        VARCHAR(6)   NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_evc_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_evc_code ON email_verification_codes (code);
