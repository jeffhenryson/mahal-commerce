-- Index para lookup por google_id no fluxo de login OAuth (evita full scan a cada autenticação).
CREATE INDEX idx_users_google_id ON users(google_id) WHERE google_id IS NOT NULL;
