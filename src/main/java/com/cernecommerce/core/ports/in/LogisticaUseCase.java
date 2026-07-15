package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.logistica.Shipment;

import java.util.List;

/**
 * Port de entrada do domínio <b>logistica</b>.
 *
 * <p>Stub — expõe apenas uma leitura. Casos de uso previstos (TODO):
 * {@code dispatch}, {@code updateStatus}, {@code assignMotoboyRoute},
 * {@code registerPickup} (clique e retire).</p>
 */
public interface LogisticaUseCase {

    /** Lista as expedições. Stub: retorna lista vazia até a implementação. */
    List<Shipment> listShipments();
}
