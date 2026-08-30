-- PDV-F011 — Permissão para lançar acréscimo manual na linha da comanda.
--
-- Mesmo raciocínio da V115, na direção oposta: se lançar uma linha a zero é um desconto de 100% e
-- tem dono (PDV_COMANDA_COURTESY), subir o preço à mão também tem. O valor do acréscimo é decidido
-- no balcão, caso a caso, sem tabela que o justifique depois — é exatamente o tipo de lançamento
-- que precisa de um nome atrás dele quando o fechamento não bater.
--
-- Concedida só ao ROLE_ADMIN, como a V115. Estender ao atendente depois é uma migration de uma
-- linha; recolher uma permissão já distribuída é o caminho caro.

INSERT INTO permissions (name) VALUES ('PDV_COMANDA_SURCHARGE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'PDV_COMANDA_SURCHARGE'
ON CONFLICT DO NOTHING;
