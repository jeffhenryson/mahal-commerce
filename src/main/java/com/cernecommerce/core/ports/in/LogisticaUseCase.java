package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.logistica.Shipment;

/**
 * Port de entrada do domínio <b>logistica</b>.
 *
 * <p>Stub — expõe apenas uma leitura. Casos de uso previstos (TODO):
 * {@code dispatch}, {@code updateStatus}, {@code assignMotoboyRoute},
 * {@code registerPickup} (clique e retire).</p>
 */
public interface LogisticaUseCase {

    /** Lista as expedições paginadas. Stub: retorna página vazia até a implementação. */
    PageResult<Shipment> listShipments(int page, int size);
}
