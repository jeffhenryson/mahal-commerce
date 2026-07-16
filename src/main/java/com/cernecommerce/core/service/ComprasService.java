package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
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
    public PageResult<Supplier> listSuppliers(int page, int size) {
        // TODO: delegar ao SupplierRepository.findAll(page, size).
        return new PageResult<>(List.of(), page, size, 0, 0);
    }
}
