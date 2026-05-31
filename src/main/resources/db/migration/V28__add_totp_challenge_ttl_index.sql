-- Index para o scheduler de limpeza de challenge tokens expirados (evita full scan).
CREATE INDEX idx_totp_challenge_tokens_expires_at ON totp_challenge_tokens(expires_at);
