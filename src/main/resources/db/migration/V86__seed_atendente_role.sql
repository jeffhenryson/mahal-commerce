-- ROLE_ATENDENTE: perfil de operador de PDV — abre/fecha venda e sessão de caixa,
-- consulta produto/estoque/cliente/cashback para atender, sem acesso administrativo
-- (sem gestão de usuário/role/permissão, sem financeiro/compras/logística, sem
-- fulfillment/cancelamento/reembolso de pedido).
INSERT INTO permissions (name) VALUES
    ('PDV_READ'),
    ('PDV_SALE_MANAGE'),
    ('PDV_SALE_DISCOUNT'),
    ('PDV_SESSION_MANAGE'),
    ('PDV_SESSION_CLOSE'),
    ('ESTOQUE_PRODUCT_READ'),
    ('ESTOQUE_WAREHOUSE_READ'),
    ('ESTOQUE_RESERVATION_READ'),
    ('CRM_CUSTOMER_READ'),
    ('CRM_CUSTOMER_LOOKUP'),
    ('CASHBACK_READ'),
    ('ORDER_READ')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (name) VALUES ('ROLE_ATENDENTE') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ATENDENTE'
  AND p.name IN (
      'PDV_READ', 'PDV_SALE_MANAGE', 'PDV_SALE_DISCOUNT',
      'PDV_SESSION_MANAGE', 'PDV_SESSION_CLOSE',
      'ESTOQUE_PRODUCT_READ', 'ESTOQUE_WAREHOUSE_READ', 'ESTOQUE_RESERVATION_READ',
      'CRM_CUSTOMER_READ', 'CRM_CUSTOMER_LOOKUP',
      'CASHBACK_READ',
      'ORDER_READ'
  )
ON CONFLICT DO NOTHING;
