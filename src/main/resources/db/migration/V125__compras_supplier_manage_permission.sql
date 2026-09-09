-- COM-F001 — permissão de cadastro de fornecedor.
--
-- Própria, e não reaproveitando COMPRAS_RECEIPT_MANAGE, porque as duas coisas têm dono diferente
-- na operação: receber mercadoria é rotina de quem confere a nota no balcão; cadastrar fornecedor
-- grava CNPJ, que é dado de compliance e entra em nota fiscal. Quem recebe não precisa poder
-- cadastrar, e o inverso também vale.
--
-- ON CONFLICT DO NOTHING em toda migration de permissão, como manda a nota deixada em EST-C006 e
-- como já fazem V56, V57, V60, V105, V111, V115, V117 e V119.

INSERT INTO permissions (name) VALUES ('COMPRAS_SUPPLIER_MANAGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'COMPRAS_SUPPLIER_MANAGE'
ON CONFLICT DO NOTHING;
