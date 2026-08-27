-- Permite usuários OAuth sem senha local
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- Provedor de autenticação original (LOCAL = usuário/senha, GOOGLE = OAuth Google)
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

-- ID do usuário no Google (nullable — apenas usuários com login via Google têm)
ALTER TABLE users ADD COLUMN google_id VARCHAR(255);

-- Garante unicidade do google_id ignorando NULLs (múltiplas contas sem Google ficam OK)
CREATE UNIQUE INDEX uk_users_google_id ON users (google_id) WHERE google_id IS NOT NULL;
