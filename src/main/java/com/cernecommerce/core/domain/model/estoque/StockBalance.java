package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Saldo de um SKU em um {@link Warehouse}. {@code version} suporta locking otimista
 * para as escritas concorrentes que serão introduzidas pela movimentação de estoque (F003).
 */
public record StockBalance(Long id, String sku, Long warehouseId, BigDecimal quantity, long version) {

    public StockBalance {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("quantity não pode ser negativa");
        }
    }

    /** Saldo inicial (zero) de um SKU em um depósito, quando ainda não houve nenhuma movimentação. */
    public static StockBalance zero(String sku, Long warehouseId) {
        return new StockBalance(null, sku, warehouseId, BigDecimal.ZERO, 0L);
    }

    /** Reconstitui um saldo a partir de persistência. */
    public static StockBalance of(Long id, String sku, Long warehouseId, BigDecimal quantity, long version) {
        return new StockBalance(id, sku, warehouseId, quantity, version);
    }
}
