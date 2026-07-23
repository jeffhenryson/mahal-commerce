package com.cernecommerce.core.domain.exception.estoque;

import java.math.BigDecimal;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku, Long warehouseId, BigDecimal currentQuantity,
            BigDecimal requestedQuantity) {
        super("Saldo insuficiente para o SKU " + sku + " no depósito " + warehouseId
                + ": saldo atual " + currentQuantity + ", quantidade solicitada " + requestedQuantity);
    }
}
