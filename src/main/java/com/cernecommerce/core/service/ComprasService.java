package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.ports.in.ComprasUseCase;

import java.util.List;

/**
 * Implementação stub do {@link ComprasUseCase}.
 *
 * <p>Classe pura conectada via {@code @Bean} em {@code CoreBeanConfig}. Quando o
 * adapter de persistência existir, injetar {@code SupplierRepository} pelo
 * construtor (ver {@code core.ports.out.compras}).</p>
 */
public class ComprasService implements ComprasUseCase {

    // TODO: injetar SupplierRepository (core.ports.out.compras) quando o adapter existir.

    @Override
    public List<Supplier> listSuppliers() {
        // TODO: delegar ao SupplierRepository.findAll().
        return List.of();
    }
}
