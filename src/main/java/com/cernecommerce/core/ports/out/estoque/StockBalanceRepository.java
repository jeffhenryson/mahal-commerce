package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.StockBalance;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Port de saída para persistência de saldo de estoque por SKU/depósito.
 */
public interface StockBalanceRepository {

    Optional<StockBalance> findBySkuAndWarehouseId(String sku, Long warehouseId);

    /** Saldo paginado de todos os produtos de um depósito, ordenado por SKU. */
    PageResult<StockBalance> findByWarehouseId(Long warehouseId, int page, int size);

    StockBalance save(StockBalance stockBalance);

    /**
     * Soma {@code quantity * costPrice efetivo} de todo saldo em todos os depósitos — o
     * {@code costPrice efetivo} segue a mesma precedência variação→pai de
     * {@code Product.effectivePricingFor}. Saldo cujo SKU não resolve a nenhum custo conhecido
     * contribui zero, não é excluído da soma nem a invalida.
     */
    BigDecimal sumInventoryValueAtCost();
}
