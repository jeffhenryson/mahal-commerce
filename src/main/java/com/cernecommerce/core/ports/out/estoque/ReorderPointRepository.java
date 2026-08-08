package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;

import java.util.Optional;

/**
 * Port de saída para persistência de pontos de reposição de estoque por SKU/depósito.
 */
public interface ReorderPointRepository {

    Optional<ReorderPoint> findBySkuAndWarehouseId(String sku, Long warehouseId);

    /** Pontos de reposição configurados num depósito, paginados, ordenados por SKU. */
    PageResult<ReorderPoint> findByWarehouseId(Long warehouseId, int page, int size);

    ReorderPoint save(ReorderPoint reorderPoint);
}
