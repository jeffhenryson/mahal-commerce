package com.cernecommerce.core.ports.out.logistica;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.logistica.Shipment;

import java.util.Optional;

/**
 * Port de saída para persistência de expedições.
 *
 * <p>Stub — a ser implementado por um adapter em {@code adapter/out/persistence}.</p>
 */
public interface ShipmentRepository {

    PageResult<Shipment> findAll(int page, int size);

    Optional<Shipment> findByOrderRef(String orderRef);

    Shipment save(Shipment shipment);
}
