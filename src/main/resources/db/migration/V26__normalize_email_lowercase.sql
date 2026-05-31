-- Normaliza emails para lowercase: impede contas duplicadas com variações de capitalização
-- e garante que o índice único case-sensitive funcione corretamente.
UPDATE users SET email = LOWER(TRIM(email)) WHERE email IS NOT NULL;
UPDATE users SET pending_email = LOWER(TRIM(pending_email)) WHERE pending_email IS NOT NULL;
