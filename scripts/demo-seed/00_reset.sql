-- 00_reset.sql — limpa as tabelas de negócio antes de recarregar os dados de demonstração.
--
-- Estratégia: regenerar tudo do zero (não é viável fazer ON CONFLICT linha a linha em milhares
-- de registros interdependentes). roles/permissions/role_permissions NUNCA são tocadas (geridas
-- só por migration/bootstrap Java). Usuários OPERADOR "reais" (desenvolvedor/administrador/
-- atendente/usuario, criados por scripts/rename-and-seed-users.sql) também são preservados —
-- só usuários com username 'demo.%' (criados por esta pipeline) são removidos.
--
-- Contas de cliente (user_type='CUSTOMER') são todas removidas antes do TRUNCATE de customers,
-- porque customers é recriada do zero a cada rodada (não há necessidade de preservar clientes
-- pré-existentes em hml — são, na pior das hipóteses, sobras de execução manual das coleções
-- Postman, que o próprio docs/postman/README.md documenta não terem endpoint de exclusão).

BEGIN;

DELETE FROM users WHERE user_type = 'CUSTOMER';

TRUNCATE TABLE
    cashback_entry,
    order_payment,
    comanda_item,
    comanda,
    order_item,
    sales_order,
    cash_movement,
    cash_register_session,
    cash_flow_entry,
    nfe_import_line,
    nfe_import,
    goods_receipt_item,
    goods_receipt,
    stock_reservation,
    stock_lot,
    stock_count_item,
    stock_count,
    stock_movement,
    stock_reorder_point,
    stock_balance,
    cart_item,
    cart,
    product_kit_component,
    product_variant,
    product_attribute,
    product_root_attribute,
    product_image,
    product,
    product_category,
    campaign_log,
    campaign_automations,
    customer_tags,
    tags,
    customer_notes,
    customer_stage_transitions,
    customers,
    supplier,
    warehouse
    RESTART IDENTITY CASCADE;

DELETE FROM users WHERE username LIKE 'demo.%';

COMMIT;
