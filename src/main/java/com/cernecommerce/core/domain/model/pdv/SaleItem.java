package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;

/**
 * Item de uma {@link Sale}: SKU vendido, quantidade e preço unitário praticado.
 */
public record SaleItem(String sku, BigDecimal quantity, BigDecimal unitPrice) {

    public SaleItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice não pode ser negativo");
        }
    }

    /** Subtotal do item (quantity * unitPrice). */
    public BigDecimal subtotal() {
        return quantity.multiply(unitPrice);
    }
}
