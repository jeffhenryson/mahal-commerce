-- ECM-F002 (catálogo público) e futuramente ECM-F003 (checkout): a operação real tem um
-- depósito físico só (plano-pdv-marketplace.md §2.2), e a superfície pública não tem sessão de
-- operador para informar um warehouseCode. Este é o código do depósito que o catálogo e o
-- checkout devem usar.
--
-- Semeado em branco de propósito: criar um depósito é ação do operador (POST
-- /estoque/warehouses), nenhuma migration tem como inventar um código real. O valor em branco só
-- torna a chave descobrível via GET /system/config (DEV_ELEVATED); EstoqueService.getDefaultWarehouse
-- trata em branco exatamente como ausente e lança DefaultWarehouseNotConfiguredException até o
-- operador configurar de verdade via PUT /system/config/estoque.warehouse.default-code.
INSERT INTO system_config (config_key, config_value) VALUES
    ('estoque.warehouse.default-code', '')
ON CONFLICT (config_key) DO NOTHING;
