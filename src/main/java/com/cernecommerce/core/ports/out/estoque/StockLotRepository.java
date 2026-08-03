package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.StockLot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência do lote/validade de um SKU/depósito (EST-F008).
 */
public interface StockLotRepository {

    Optional<StockLot> findBySkuAndWarehouseIdAndLotCode(String sku, Long warehouseId, String lotCode);

    /** Ordenado por {@code expiryDate ASC, id ASC} — a ordem que o consumo FEFO percorre. */
    List<StockLot> findBySkuAndWarehouseId(String sku, Long warehouseId);

    Optional<StockLot> findById(Long id);

    StockLot save(StockLot stockLot);

    /**
     * Lotes com saldo ainda não escoado cujo vencimento cai em {@code cutoff} ou antes, e que
     * ainda não foram alertados ({@code alertedAt IS NULL}). Usado pelo scheduler de vencimento
     * próximo (EST-F008) — o dedupe por {@code alertedAt} é o que evita alertar o mesmo lote
     * todo dia.
     */
    List<StockLot> findExpiringSoon(LocalDate cutoff, int batchSize);
}
