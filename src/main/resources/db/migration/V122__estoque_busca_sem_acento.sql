-- EST-C020 — busca de produto passa a ignorar acento, como já ignorava caixa.
--
-- O sintoma medido no QA de 06/09/2026 não era só "não encontrar": como há produto cadastrado com
-- e sem acento, cada busca devolvia apenas o seu grupo. `search=Narguilé` trazia 8 e
-- `search=narguile` trazia 6, de um total de 14 — uma lista plausível, incompleta e sem nenhum
-- sinal de que faltava metade. `search=carvao` e `search=essencia` devolviam zero.
--
-- A extensão é `trusted` desde o Postgres 13, então não exige superusuário em banco gerenciado.
-- IF NOT EXISTS porque a extensão pode já ter sido criada por outro caminho (imagem, template).
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Sem índice, de propósito, mantendo a decisão da V87: a busca textual do catálogo não é indexada
-- enquanto o catálogo couber num LIKE sequencial, e o caminho registrado para quando não couber
-- continua sendo pg_trgm + GIN. Um índice funcional sobre unaccent() exigiria antes empacotá-la
-- numa função IMMUTABLE própria, porque unaccent() é STABLE — trabalho que só se paga junto com a
-- troca para pg_trgm.
