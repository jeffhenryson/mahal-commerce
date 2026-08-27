-- Índice composto para queries de intervalo de data em audit_logs.
-- Suporta filtros combinados (username + timestamp) e range-only (timestamp).
-- A coluna timestamp DESC já existe como índice simples (V18); este índice cobre
-- os casos de uso mais frequentes da API (filtrar por usuário + período).
CREATE INDEX idx_audit_logs_username_timestamp ON audit_logs (username, timestamp DESC);
