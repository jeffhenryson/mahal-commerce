package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Warehouse;

import java.util.Optional;

/**
 * Port de saída para persistência de depósitos de estoque.
 */
public interface WarehouseRepository {

    Warehouse save(Warehouse warehouse);

    Optional<Warehouse> findByCode(String code);

    /**
     * Busca por id. Necessária porque {@code StockCount} e {@code StockBalance} guardam
     * {@code warehouseId}, enquanto a API trabalha com {@code code}.
     */
    Optional<Warehouse> findById(Long id);

    /** Depósitos paginados, ordenados por id. */
    PageResult<Warehouse> findAll(int page, int size);
}
