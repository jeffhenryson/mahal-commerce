-- Índices para as queries de emissão/limpeza por username (findFirstByUsernameOrderBy...,
-- deleteByUsername) — sem eles, ambas fazem full table scan conforme as tabelas crescem.
CREATE INDEX idx_email_verification_codes_username ON email_verification_codes(username);
CREATE INDEX idx_password_reset_tokens_username ON password_reset_tokens(username);
