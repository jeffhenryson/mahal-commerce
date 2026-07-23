package com.cernecommerce.core.domain.model.compras;

import java.math.BigDecimal;

/**
 * Item de um {@link GoodsReceipt}: quantidade recebida de um SKU.
 */
public record GoodsReceiptItem(String sku, BigDecimal quantity) {

    public GoodsReceiptItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
    }
}
