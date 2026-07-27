package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.StockCount;

import java.util.Optional;

/**
 * Port de saída para persistência dos balanços de inventário (EST-F006).
 */
public interface StockCountRepository {

    StockCount save(StockCount stockCount);

    Optional<StockCount> findById(Long id);

    /**
     * Balanço <b>aberto</b> do depósito, se houver. Sustenta a regra de uma contagem aberta por
     * depósito: duas simultâneas sobre o mesmo saldo se sobrescreveriam no fechamento.
     */
    Optional<StockCount> findOpenByWarehouseId(Long warehouseId);

    /** Balanços do depósito, dos mais recentes para os mais antigos. */
    PageResult<StockCount> findByWarehouseId(Long warehouseId, int page, int size);
}
