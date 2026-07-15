package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.logistica.Shipment;
import com.cernecommerce.core.ports.in.LogisticaUseCase;

import java.util.List;

/**
 * Implementação stub do {@link LogisticaUseCase}.
 *
 * <p>Classe pura conectada via {@code @Bean} em {@code CoreBeanConfig}. Quando os
 * adapters existirem, injetar {@code ShipmentRepository} e {@code CarrierPort} pelo
 * construtor (ver {@code core.ports.out.logistica}).</p>
 */
public class LogisticaService implements LogisticaUseCase {

    // TODO: injetar ShipmentRepository / CarrierPort (core.ports.out.logistica).

    @Override
    public List<Shipment> listShipments() {
        // TODO: delegar ao ShipmentRepository.findAll().
        return List.of();
    }
}
