package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.StockMovement;

/**
 * Port de saída para persistência de movimentações de estoque (trilha de auditoria de
 * alterações de {@link com.cernecommerce.core.domain.model.estoque.StockBalance}).
 */
public interface StockMovementRepository {

    StockMovement save(StockMovement movement);

    /** Histórico paginado de movimentações de um SKU em um depósito, mais recentes primeiro. */
    PageResult<StockMovement> findBySkuAndWarehouseId(String sku, Long warehouseId, int page, int size);
}
