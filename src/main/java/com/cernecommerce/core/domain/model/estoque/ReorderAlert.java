package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Um SKU que cruzou o ponto de reposição para baixo numa movimentação. Acumulado durante a
 * operação e despachado em lote após o commit — ver {@code EstoqueService.notifyIfBelowReorderPoint}.
 */
public record ReorderAlert(String sku, BigDecimal quantity, BigDecimal minQuantity) {

    public ReorderAlert {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("quantity é obrigatória");
        }
        if (minQuantity == null) {
            throw new IllegalArgumentException("minQuantity é obrigatória");
        }
    }
}
