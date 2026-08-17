-- Notificação de kit bloqueado (buildable > 0 -> 0, Bloco 1.2 do BACKEND_TODO de mahal-admin)
-- roda findKitSkusByComponentSku a cada movimentação/reserva de QUALQUER sku (não só componentes
-- de kit). uk_kit_component (kit_sku, component_sku) não serve essa busca — component_sku é a
-- segunda coluna do composto. Sem índice dedicado, cada adjustStock/reserveStock/consumeReservation
-- faria um seq scan em product_kit_component, mesmo quando o sku não é componente de kit nenhum.
CREATE INDEX idx_kit_component_component_sku ON product_kit_component (component_sku);
