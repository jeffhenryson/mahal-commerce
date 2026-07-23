package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Ponto de reposição (quantidade mínima) de um SKU em um depósito. Quando o
 * {@link StockBalance} correspondente cai abaixo de {@code minQuantity}, uma notificação de
 * reposição deve ser disparada.
 */
public record ReorderPoint(Long id, String sku, Long warehouseId, BigDecimal minQuantity) {

    public ReorderPoint {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (minQuantity == null || minQuantity.signum() < 0) {
            throw new IllegalArgumentException("minQuantity deve ser maior ou igual a zero");
        }
    }

    /** Cria um novo ponto de reposição (sem id). */
    public static ReorderPoint create(String sku, Long warehouseId, BigDecimal minQuantity) {
        return new ReorderPoint(null, sku, warehouseId, minQuantity);
    }

    /** Reconstitui um ponto de reposição a partir de persistência. */
    public static ReorderPoint of(Long id, String sku, Long warehouseId, BigDecimal minQuantity) {
        return new ReorderPoint(id, sku, warehouseId, minQuantity);
    }

    /** {@code true} se {@code quantity} está estritamente abaixo do ponto de reposição. */
    public boolean isBelow(BigDecimal quantity) {
        return quantity.compareTo(minQuantity) < 0;
    }
}
