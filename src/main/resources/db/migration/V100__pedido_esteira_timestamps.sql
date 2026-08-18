-- Timestamps por etapa da esteira de fulfillment (Docs/BACKEND_TODO.md do mahal-admin,
-- §"Vendas: timestamps por etapa da esteira") — createdAt/paidAt/concludedAt/cancelledAt já
-- existiam, mas SEPARADO/ENVIADO/ENTREGUE não tinham data própria. Histórico, sem CHECK de
-- coexistência com o status atual — mesma régua de reserved_at/paid_at.
ALTER TABLE sales_order ADD COLUMN separated_at TIMESTAMP;
ALTER TABLE sales_order ADD COLUMN shipped_at TIMESTAMP;
ALTER TABLE sales_order ADD COLUMN delivered_at TIMESTAMP;
