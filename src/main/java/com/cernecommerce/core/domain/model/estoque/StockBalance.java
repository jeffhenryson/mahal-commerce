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
     * Aplica uma movimentação e retorna o saldo resultante.
     *
     * <ul>
     *   <li>{@code ENTRADA} — soma {@code movementQuantity} ao saldo.</li>
     *   <li>{@code SAIDA} — subtrai. Lança {@link InsufficientStockException} se o resultado
     *       ficaria negativo; zerar exatamente é permitido.</li>
     *   <li>{@code AJUSTE} — <b>substitui</b> o saldo por {@code movementQuantity}, que é o valor
     *       contado na prateleira, não um delta (EST-C009). Sobe ou desce, e zero é válido.</li>
     * </ul>
     *
     * <p>Um {@code AJUSTE} negativo é {@link IllegalArgumentException}, e não
     * {@code InsufficientStockException}: não existe saldo insuficiente para uma contagem — o que
     * há é um alvo inválido.</p>
     */
    public StockBalance apply(MovementType type, BigDecimal movementQuantity) {
        if (type == MovementType.AJUSTE) {
            if (movementQuantity == null || movementQuantity.signum() < 0) {
                throw new IllegalArgumentException("quantidade de AJUSTE não pode ser negativa");
            }
            return new StockBalance(id, sku, warehouseId, movementQuantity, version);
        }
        BigDecimal newQuantity = type == MovementType.SAIDA
                ? quantity.subtract(movementQuantity)
                : quantity.add(movementQuantity);
        if (newQuantity.signum() < 0) {
            throw new InsufficientStockException(sku, warehouseId, quantity, movementQuantity);
        }
        return new StockBalance(id, sku, warehouseId, newQuantity, version);
    }
}
