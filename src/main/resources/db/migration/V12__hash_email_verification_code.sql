-- Aumenta a coluna code para acomodar hash SHA-256 em base64url (43 chars) em vez de
-- código numérico de 6 dígitos em plaintext. Também aumenta a entropia do código gerado
-- (8 chars alfanuméricos maiúsculos no lado da aplicação).
ALTER TABLE email_verification_codes
    ALTER COLUMN code TYPE VARCHAR(64);

-- Rastreia quando o código foi enviado para aplicar cooldown de reenvio por destinatário.
ALTER TABLE email_verification_codes
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
