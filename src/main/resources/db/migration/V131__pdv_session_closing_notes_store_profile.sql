-- Encerramento de caixa pelo módulo de Vendas e perfil da loja para o cupom.
--
-- closing_notes: motivo/observação do fechamento. Opcional; é preenchido sobretudo quando admin ou
-- dev encerra o caixa de outro operador (quem saiu sem fechar). Mesmo limite do request (500).
ALTER TABLE cash_register_session ADD COLUMN closing_notes VARCHAR(500);

-- STORE_PROFILE_MANAGE: editar nome, CNPJ, endereço e rodapé impressos no cupom. Leitura do perfil
-- é livre para qualquer usuário autenticado — o PDV imprime o cabeçalho sem ser admin.
INSERT INTO permissions (name) VALUES ('STORE_PROFILE_MANAGE') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV')
  AND p.name = 'STORE_PROFILE_MANAGE'
ON CONFLICT DO NOTHING;
