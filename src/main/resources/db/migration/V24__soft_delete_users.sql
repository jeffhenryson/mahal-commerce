-- Soft delete: registros deletados ficam no banco com deleted_at preenchido.
-- Todas as queries filtram WHERE deleted_at IS NULL via @SQLRestriction no UserEntity.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;

-- Índice parcial: acelera leitura de usuários ativos (o caso dominante) sem overhead nos deletes.
CREATE INDEX idx_users_active ON users (id) WHERE deleted_at IS NULL;
