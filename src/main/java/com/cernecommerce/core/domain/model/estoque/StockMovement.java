package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro auditável de uma movimentação manual de estoque (entrada, saída ou ajuste) que
 * altera o {@link StockBalance} de um SKU em um depósito.
 */
public record StockMovement(Long id, String sku, Long warehouseId, MovementType type, BigDecimal quantity,
        String reason, String username, Instant createdAt) {

    public StockMovement {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (type == null) {
            throw new IllegalArgumentException("type é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason é obrigatório");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username é obrigatório");
        }
    }

    /** Cria uma nova movimentação (sem id, createdAt no momento atual). */
    public static StockMovement create(String sku, Long warehouseId, MovementType type, BigDecimal quantity,
            String reason, String username) {
        return new StockMovement(null, sku, warehouseId, type, quantity, reason, username, Instant.now());
    }

    /** Reconstitui uma movimentação a partir de persistência. */
    public static StockMovement of(Long id, String sku, Long warehouseId, MovementType type, BigDecimal quantity,
            String reason, String username, Instant createdAt) {
        return new StockMovement(id, sku, warehouseId, type, quantity, reason, username, createdAt);
    }
}
