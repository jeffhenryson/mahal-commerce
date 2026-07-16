package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.StockBalance;

import java.util.Optional;

/**
 * Port de saída para persistência de saldo de estoque por SKU/depósito.
 */
public interface StockBalanceRepository {

    Optional<StockBalance> findBySkuAndWarehouseId(String sku, Long warehouseId);

    StockBalance save(StockBalance stockBalance);
}
