package com.cernecommerce.core.domain.model.logistica;

import java.time.Instant;

/**
 * Expedição de um pedido: acompanha o status do despacho (separação → em rota →
 * entregue / retirado). Cobre entrega por motoboy, transportadora e clique e retire.
 *
 * <p>Stub de domínio — campos representativos.</p>
 */
public record Shipment(
    Long id,
    String orderRef,
    Mode mode,
    Status status,
    Instant updatedAt
) {
    public enum Mode { MOTOBOY, CARRIER, PICKUP }

    public enum Status { PICKING, DISPATCHED, IN_TRANSIT, DELIVERED, CANCELLED }
}
