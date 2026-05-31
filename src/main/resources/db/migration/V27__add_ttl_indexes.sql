-- Indexes para queries de cleanup por expiração (schedulers deletam por expires_at).
-- Sem esses índices, o cleanup faz full scan conforme as tabelas crescem.
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_email_verification_codes_expires_at ON email_verification_codes(expires_at);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);
