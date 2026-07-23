package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;

import java.math.BigDecimal;

/**
 * Saldo de um SKU em um {@link Warehouse}. {@code version} suporta locking otimista para as
 * escritas concorrentes de {@link StockMovement} (movimentação manual de estoque).
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

    /**
     * Aplica uma movimentação (entrada, saída ou ajuste) e retorna o saldo resultante.
     * {@code ENTRADA} e {@code AJUSTE} somam a quantidade; {@code SAIDA} subtrai. Lança
     * {@link InsufficientStockException} se o resultado ficaria negativo — saldo zerado é
     * permitido, negativo não.
     */
    public StockBalance apply(MovementType type, BigDecimal movementQuantity) {
        BigDecimal newQuantity = type == MovementType.SAIDA
                ? quantity.subtract(movementQuantity)
                : quantity.add(movementQuantity);
        if (newQuantity.signum() < 0) {
            throw new InsufficientStockException(sku, warehouseId, quantity, movementQuantity);
        }
        return new StockBalance(id, sku, warehouseId, newQuantity, version);
    }
}
