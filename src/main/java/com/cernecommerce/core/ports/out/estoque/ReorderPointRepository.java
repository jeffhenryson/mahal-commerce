package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.ReorderPoint;

import java.util.Optional;

/**
 * Port de saída para persistência de pontos de reposição de estoque por SKU/depósito.
 */
public interface ReorderPointRepository {

    Optional<ReorderPoint> findBySkuAndWarehouseId(String sku, Long warehouseId);

    ReorderPoint save(ReorderPoint reorderPoint);
}
