-- CRM-C007 — lookup de cliente por telefone/e-mail/CPF devolvendo TODOS os que batem.
--
-- contato é gravado como o operador digitou ("(83) 99999-0000"), e até aqui a comparação por
-- dígitos era feita na consulta com uma cadeia de replace() — sem índice, varrendo a tabela. A
-- coluna phone_normalized guarda só os dígitos, sem o DDI 55, preenchida pela aplicação em todo
-- save (CustomerIdentifiers.normalizePhone). Número nacional tem 10 ou 11 dígitos, então só um total
-- de 12 ou 13 começando com 55 carrega DDI — o DDD 55 (RS) sozinho nunca passa de 11.
--
-- Índice NÃO único de propósito: telefone pode ser compartilhado (família, número corporativo) e
-- a base já pode ter repetidos — um UNIQUE faria esta migração falhar. A duplicidade no cadastro
-- é barrada no serviço (409 CUSTOMER_ALREADY_EXISTS com matchedBy/customerId).

ALTER TABLE customers ADD COLUMN phone_normalized VARCHAR(30);

UPDATE customers c
SET phone_normalized = CASE
        WHEN length(d.digits) IN (12, 13) AND d.digits LIKE '55%' THEN substring(d.digits FROM 3)
        ELSE d.digits
    END
FROM (SELECT id, NULLIF(regexp_replace(contato, '\D', '', 'g'), '') AS digits
      FROM customers WHERE contato IS NOT NULL) d
WHERE c.id = d.id;

CREATE INDEX idx_customers_phone_normalized ON customers (phone_normalized);

-- E-mail é comparado sem diferenciar maiúsculas ("Maria@X.com" = "maria@x.com").
CREATE INDEX idx_customers_email_lower ON customers (lower(email));

COMMENT ON COLUMN customers.phone_normalized IS
    'contato só com dígitos e sem DDI 55, mantido pela aplicação (CustomerIdentifiers.normalizePhone). Usado no lookup e na checagem de duplicidade do cadastro. Não é único: telefone pode ser compartilhado.';
