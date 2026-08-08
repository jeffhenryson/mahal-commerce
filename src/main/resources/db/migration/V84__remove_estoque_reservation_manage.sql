-- Remover permissão órfã ESTOQUE_RESERVATION_MANAGE
-- A permissão foi seedada em V64, mas nunca foi referenciada em nenhum @PreAuthorize
-- As operações de reserva (reserveStock, consumeReservation, releaseReservation) são
-- orquestradas internamente por OrderService, ShopService, PdvService e PaymentWebhookService,
-- nunca expostas como endpoint administrativo direto.
DELETE FROM role_permissions
WHERE permission_id = (SELECT id FROM permissions WHERE name = 'ESTOQUE_RESERVATION_MANAGE');

DELETE FROM permissions
WHERE name = 'ESTOQUE_RESERVATION_MANAGE';
